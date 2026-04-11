package com.example.demo02criticalissues.retriever;

import com.example.demo02criticalissues.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 *  ClassName: IRCoTRetriever
 *  Package: com.example.demo02criticalissues.retriever
 *
 *  @Author Mrchen
 *
 * IRCoT 迭代检索器
 * 通过交错检索与思维链来解决需要多级跳转的复杂查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IRCoTRetriever {

    private final ChatLanguageModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${retrieval.top-k:5}")
    private int topK;

    @Value("${retrieval.ircot.max-iterations:5}")
    private int maxIterations;

    private static final String THOUGHT_PROMPT = """
            你是一个严谨的信息检索与推理侦探。请分析当前问题，判断基于【已检索到的信息】是否已经能够完全、确切地得出最终答案。

            当前问题：%s
            已检索到的信息：%s
            已思考步骤：%s

            【极其重要的警告】：
            1. 绝对不要使用你的先验知识进行猜测或脑补！所有结论必须完全来源于【已检索到的信息】。
            2. 如果已知信息缺少任何一个关键的推理环节（比如：知道人名但不知道他的公司，知道公司但不知道总部在哪），都必须选择继续检索，并提取缺失环节的精简关键词作为 next_query！

            如果当前信息已经无懈可击地包含完整推理链并能得出最终答案，返回 {"need_retrieval": false, "answer": "基于检索结果得出的最终答案"}
            如果需要继续挖掘缺失线索，返回 {"need_retrieval": true, "next_query": "下一个要搜索的精简关键词（例如 '影刃 真实身份' 或 '某财团 总部'）", "reasoning": "说明当前缺什么，为什么需要搜这个词"}
            
            请严格只返回纯JSON格式数据，不要包含任何 Markdown 标记或多余文字。
            """;

    private static final String ANSWER_SYNTHESIS_PROMPT = """
            你是一个信息综合专家。请基于以下检索到的信息，综合回答用户的问题。

            用户问题：%s
            检索到的信息：%s
            思考过程：%s

            请提供详细、准确的答案。
            """;

    public record IRCoTResult(
            String answer,
            List<RetrievalStep> steps,
            boolean successful
    ) {}

    public record RetrievalStep(
            int iteration,
            String query,
            String reasoning,
            List<TextSegment> retrievedDocs,
            boolean sufficient
    ) {}

    public IRCoTResult retrieve(String userQuery) {
        log.info("开始IRCoT迭代检索 - 问题: {}", userQuery);

        List<RetrievalStep> steps = new ArrayList<>();
        String currentQuery = userQuery;
        String thinkingProcess = "";
        List<TextSegment> allRetrievedDocs = new ArrayList<>();

        for (int i = 0; i < maxIterations; i++) {
            List<TextSegment> retrievedDocs = retrieveDocuments(currentQuery, topK);
            allRetrievedDocs.addAll(retrievedDocs);

            String retrievedInfo = summarizeRetrievedDocs(retrievedDocs);
            ThoughtResult thoughtResult = generateThought(userQuery, retrievedInfo, thinkingProcess);

            steps.add(new RetrievalStep(
                    i + 1,
                    currentQuery,
                    thoughtResult.reasoning(),
                    retrievedDocs,
                    !thoughtResult.needRetrieval()
            ));

            thinkingProcess += "\n步骤" + (i + 1) + ": " + thoughtResult.reasoning();

            if (!thoughtResult.needRetrieval()) {
                String answer = synthesizeAnswer(userQuery, retrievedInfo, thinkingProcess);
                log.info("IRCoT检索完成 - 迭代次数: {}", i + 1);
                return new IRCoTResult(answer, steps, true);
            }

            currentQuery = thoughtResult.nextQuery();
            log.info("IRCoT迭代 {} - 下一轮查询: {}", i + 1, currentQuery);
        }

        String finalRetrievedInfo = summarizeRetrievedDocs(allRetrievedDocs);
        String answer = synthesizeAnswer(userQuery, finalRetrievedInfo, thinkingProcess);

        log.warn("IRCoT达到最大迭代次数，使用综合信息生成答案");
        return new IRCoTResult(answer, steps, true);
    }

    private List<TextSegment> retrieveDocuments(String query, int topK) {
        try {
            Embedding embedding = embeddingModel.embed(query).content();

            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding)
                    .maxResults(topK)
                    .build();

            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
            List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

            return matches.stream()
                    .map(EmbeddingMatch::embedded)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("检索文档失败", e);
            return List.of();
        }
    }

    private ThoughtResult generateThought(String userQuery, String retrievedInfo, String thinkingProcess) {
        try {
            String prompt = THOUGHT_PROMPT.formatted(userQuery, retrievedInfo, thinkingProcess);
            String response = chatModel.generate(prompt);

            String cleanResponse = JsonUtils.extractJson(response);
            JsonNode jsonNode = objectMapper.readTree(cleanResponse);

            // 防止因模型幻觉丢失关键属性引发 NullPointerException 崩溃系统
            boolean needRetrieval = jsonNode.has("need_retrieval") && jsonNode.get("need_retrieval").asBoolean();
            String reasoning = jsonNode.has("reasoning") ? jsonNode.get("reasoning").asText() : "系统自动推理中";

            if (!needRetrieval) {
                String answer = jsonNode.has("answer") ? jsonNode.get("answer").asText() : "";
                return new ThoughtResult(needRetrieval, "", reasoning, answer);
            } else {
                String nextQuery = jsonNode.has("next_query") ? jsonNode.get("next_query").asText() : "";
                return new ThoughtResult(needRetrieval, nextQuery, reasoning, "");
            }

        } catch (Exception e) {
            log.error("生成思考结果失败, 原始响应可能非JSON格式", e);
            return new ThoughtResult(false, "", "思考生成失败或格式异常", "");
        }
    }

    private String synthesizeAnswer(String userQuery, String retrievedInfo, String thinkingProcess) {
        try {
            String prompt = ANSWER_SYNTHESIS_PROMPT.formatted(userQuery, retrievedInfo, thinkingProcess);
            return chatModel.generate(prompt);
        } catch (Exception e) {
            log.error("综合答案失败", e);
            return "答案综合失败: " + e.getMessage();
        }
    }

    private String summarizeRetrievedDocs(List<TextSegment> docs) {
        if (docs.isEmpty()) {
            return "未检索到相关信息";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(docs.size(), 3); i++) {
            sb.append("文档").append(i + 1).append(": ")
                    .append(docs.get(i).text())
                    .append("\n");
        }
        if (docs.size() > 3) {
            sb.append("... (还有").append(docs.size() - 3).append("个文档)");
        }
        return sb.toString();
    }

    private record ThoughtResult(
            boolean needRetrieval,
            String nextQuery,
            String reasoning,
            String answer
    ) {}
}