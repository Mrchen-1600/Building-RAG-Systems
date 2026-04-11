package com.example.demo02criticalissues.service;

import com.example.demo02criticalissues.entity.SelfRagEvaluation;
import com.example.demo02criticalissues.repository.SelfRagEvaluationRepository;
import com.example.demo02criticalissues.utils.JsonUtils;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 *  ClassName: SelfRAGService
 *  Package: com.example.demo02criticalissues.service
 *
 *  @Author Mrchen
 *
 * Self-RAG服务
 * 实现自我反思的RAG系统，包含多个评估节点
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfRAGService {

    private final ChatLanguageModel chatModel;
    private final SelfRagEvaluationRepository evaluationRepository;
    private final QueryRewriteService queryRewriteService;

    @Value("${retrieval.self-rag.max-retries:3}")
    private int maxRetries;

    @Value("${retrieval.self-rag.fallback-strategy:graceful_decline}")
    private String fallbackStrategy;

    public enum EvaluationType {
        RETRIEVAL("文档相关性", "检查检索到的文档是否包含回答问题所需的信息"),
        HALLUCINATION("幻觉", "检查生成的答案是否基于参考文档"),
        ANSWER("答案有用性", "检查答案是否真正回答了用户的问题");

        private final String displayName;
        private final String description;

        EvaluationType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    public record EvaluationResult(
            EvaluationType type,
            boolean passed,
            String reasoning,
            double score
    ) {}

    public record SelfRAGResult(
            String answer,
            List<EvaluationResult> evaluations,
            int retryCount,
            String fallbackReason,
            List<TextSegment> finalRetrievedDocs
    ) {}

    private static final String RETRIEVAL_GRADER_PROMPT = """
            你是一个文档相关性评估专家。请评估检索到的文档是否包含回答用户问题所需的信息。

            用户问题：%s

            检索到的文档：
            %s

            评估标准：
            - PASS：文档包含明确、直接的信息，可以回答用户的问题
            - FAIL：文档不相关、信息不足或与问题无关

            请严格只返回纯JSON格式数据，不要包含任何 Markdown 标记或多余文字：
            {
                "result": "PASS" 或 "FAIL",
                "reasoning": "评估理由",
                "score": 相关性评分 (0-1之间)
            }
            """;

    private static final String HALLUCINATION_GRADER_PROMPT = """
            你是一个幻觉检测专家。请检查生成的答案是否完全基于参考文档。

            用户问题：%s

            生成的答案：
            %s

            参考文档：
            %s

            评估标准：
            - PASS：答案中的所有事实和信息都来自参考文档
            - FAIL：答案包含参考文档中没有的信息（幻觉）

            请严格只返回纯JSON格式数据，不要包含任何 Markdown 标记或多余文字：
            {
                "result": "PASS" 或 "FAIL",
                "reasoning": "评估理由",
                "score": 可信度评分 (0-1之间)
            }
            """;

    private static final String ANSWER_GRADER_PROMPT = """
            你是一个答案质量评估专家。请检查答案是否真正回答了用户的问题。

            用户问题：%s

            生成的答案：
            %s

            评估标准：
            - PASS：答案直接回答了用户的问题，信息完整且准确
            - FAIL：答案没有直接回答问题、信息不完整或偏离主题

            请严格只返回纯JSON格式数据，不要包含任何 Markdown 标记或多余文字：
            {
                "result": "PASS" 或 "FAIL",
                "reasoning": "评估理由",
                "score": 质量评分 (0-1之间)
            }
            """;

    public SelfRAGResult execute(String userQuery,
                                 RetrieveFunction retrieveFunc,
                                 GenerateFunction generateFunc) {
        log.info("开始Self-RAG流程 - 问题: {}", userQuery);

        List<EvaluationResult> evaluations = new ArrayList<>();
        String currentQuery = userQuery;
        String finalAnswer = null;
        String fallbackReason = null;
        List<TextSegment> finalRetrievedDocs = new ArrayList<>();

        for (int retry = 0; retry <= maxRetries; retry++) {
            List<TextSegment> retrievedDocs = retrieveFunc.retrieve(currentQuery);
            finalRetrievedDocs = retrievedDocs;

            EvaluationResult retrievalEval = evaluateRetrieval(userQuery, retrievedDocs);
            evaluations.add(retrievalEval);

            if (!retrievalEval.passed()) {
                log.warn("文档相关性评估未通过 - 理由: {}", retrievalEval.reasoning());

                if (retry < maxRetries) {
                    var rewriteResult = queryRewriteService.terminologyRewrite(currentQuery, "system");
                    if (rewriteResult.modified()) {
                        currentQuery = rewriteResult.rewrittenQuery();
                        log.info("重写查询后重试 - 新查询: {}", currentQuery);
                        continue;
                    }
                }
                fallbackReason = "文档相关性评估失败: " + retrievalEval.reasoning();
                finalAnswer = applyFallbackStrategy(userQuery, fallbackReason);
                break;
            }

            String answer = generateFunc.generate(userQuery, retrievedDocs);

            EvaluationResult hallucinationEval = evaluateHallucination(userQuery, answer, retrievedDocs);
            evaluations.add(hallucinationEval);

            if (!hallucinationEval.passed()) {
                log.warn("幻觉评估未通过 - 理由: {}", hallucinationEval.reasoning());

                fallbackReason = "检测到严重的脱离文档幻觉，直接触发系统安全熔断: " + hallucinationEval.reasoning();
                finalAnswer = applyFallbackStrategy(userQuery, fallbackReason);
                break;
            }

            EvaluationResult answerEval = evaluateAnswer(userQuery, answer);
            evaluations.add(answerEval);

            if (!answerEval.passed()) {
                log.warn("答案有用性评估未通过 - 理由: {}", answerEval.reasoning());

                if (retry < maxRetries) {
                    continue;
                }
                fallbackReason = "答案有用性评估失败: " + answerEval.reasoning();
                finalAnswer = applyFallbackStrategy(userQuery, fallbackReason);
                break;
            }

            finalAnswer = answer;
            log.info("Self-RAG流程完成 - 所有评估通过");
            break;
        }

        saveEvaluations(evaluations);

        return new SelfRAGResult(
                finalAnswer,
                evaluations,
                evaluations.stream().mapToInt(e -> e.passed() ? 0 : 1).sum(),
                fallbackReason,
                finalRetrievedDocs
        );
    }

    public EvaluationResult evaluateRetrieval(String userQuery, List<TextSegment> retrievedDocs) {
        String docsText = retrievedDocs.stream().map(TextSegment::text).limit(3)
                .reduce((s1, s2) -> s1 + "\n" + s2).orElse("未检索到文档");
        try {
            String prompt = RETRIEVAL_GRADER_PROMPT.formatted(userQuery, docsText);
            String response = chatModel.generate(prompt);
            return parseEvaluationResponse(response, EvaluationType.RETRIEVAL);
        } catch (Exception e) {
            return new EvaluationResult(EvaluationType.RETRIEVAL, true, "评估异常默认放行", 0.5);
        }
    }

    public EvaluationResult evaluateHallucination(String userQuery, String answer, List<TextSegment> retrievedDocs) {
        String docsText = retrievedDocs.stream().map(TextSegment::text).limit(5)
                .reduce((s1, s2) -> s1 + "\n" + s2).orElse("未检索到文档");
        try {
            String prompt = HALLUCINATION_GRADER_PROMPT.formatted(userQuery, answer, docsText);
            String response = chatModel.generate(prompt);
            return parseEvaluationResponse(response, EvaluationType.HALLUCINATION);
        } catch (Exception e) {
            return new EvaluationResult(EvaluationType.HALLUCINATION, true, "评估异常默认放行", 0.5);
        }
    }

    public EvaluationResult evaluateAnswer(String userQuery, String answer) {
        try {
            String prompt = ANSWER_GRADER_PROMPT.formatted(userQuery, answer);
            String response = chatModel.generate(prompt);
            return parseEvaluationResponse(response, EvaluationType.ANSWER);
        } catch (Exception e) {
            return new EvaluationResult(EvaluationType.ANSWER, true, "评估异常默认放行", 0.5);
        }
    }

    private EvaluationResult parseEvaluationResponse(String response, EvaluationType type) {
        try {
            String cleanResponse = JsonUtils.extractJson(response);
            com.fasterxml.jackson.databind.JsonNode jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(cleanResponse);

            String resultStr = jsonNode.get("result").asText();
            boolean passed = "PASS".equals(resultStr);
            String reasoning = jsonNode.has("reasoning") ? jsonNode.get("reasoning").asText() : "";
            double score = jsonNode.has("score") ? jsonNode.get("score").asDouble() : (passed ? 0.8 : 0.3);

            return new EvaluationResult(type, passed, reasoning, score);
        } catch (Exception e) {
            log.error("解析评估响应失败: {}", response, e);
            return new EvaluationResult(type, false, "解析大模型裁判输出失败（可能格式混乱），触发安全拦截", 0.0);
        }
    }

    private String applyFallbackStrategy(String userQuery, String reason) {
        return switch (fallbackStrategy) {
            case "graceful_decline" -> "【系统拦截保护】抱歉，由于检测到可能存在回答偏离或幻觉风险，我无法直接回答您的问题。建议补充更多具体背景后再次提问。";
            case "web_search" -> "本地知识库校验未通过，正在尝试调用外部搜索引擎...";
            default -> "抱歉，无法确切回答您的问题。";
        };
    }

    // 批量 saveAll，缩短数据库持有时间，依靠 SpringDataJPA 的内置事务即可
    public void saveEvaluations(List<EvaluationResult> evaluations) {
        List<SelfRagEvaluation> evalRecords = new ArrayList<>();
        for (EvaluationResult evaluation : evaluations) {
            SelfRagEvaluation evalRecord = new SelfRagEvaluation();
            evalRecord.setConversationId(0L);
            evalRecord.setEvaluationType(evaluation.type().name());
            evalRecord.setEvaluationResult(evaluation.passed() ? "pass" : "fail");
            evalRecord.setScore(evaluation.score());
            evalRecord.setReason(evaluation.reasoning());
            evalRecords.add(evalRecord);
        }
        if (!evalRecords.isEmpty()) {
            evaluationRepository.saveAll(evalRecords);
        }
    }

    @FunctionalInterface
    public interface RetrieveFunction {
        List<TextSegment> retrieve(String query);
    }

    @FunctionalInterface
    public interface GenerateFunction {
        String generate(String query, List<TextSegment> context);
    }
}