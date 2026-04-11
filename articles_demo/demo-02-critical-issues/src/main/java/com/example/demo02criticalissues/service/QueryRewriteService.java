package com.example.demo02criticalissues.service;

import com.example.demo02criticalissues.context.ContextWindow;
import com.example.demo02criticalissues.entity.QueryRewriteLog;
import com.example.demo02criticalissues.repository.QueryRewriteLogRepository;
import com.example.demo02criticalissues.utils.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 *  ClassName: QueryRewriteService
 *  Package: com.example.demo02criticalissues.service
 *
 *  @Author Mrchen
 *
 * 查询重写服务
 * 将用户口语化的表达转换为专业的检索查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRewriteService {

    private final ChatLanguageModel chatModel;
    private final QueryRewriteLogRepository rewriteLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String REWRITE_PROMPT = """
            你是一个查询优化专家。请根据上下文，将用户的提问重写为更利于向量检索的独立句子。

            要求：
            1. 补充缺失的专有名词
            2. 将口语化表达转换为专业术语
            3. 纠正错别字
            4. 根据历史对话进行上下文补全
            5. 保持问题的核心意图不变

            历史对话：
            %s

            用户当前提问：%s

            请严格只返回纯JSON格式数据，不要包含任何 Markdown 标记或多余文字：
            {
                "rewritten_query": "重写后的查询",
                "reasoning": "重写理由",
                "changes": ["改动的具体说明"]
            }
            """;

    private static final String TERMINOLOGY_PROMPT = """
            你是一个术语规范化专家。请将用户的提问中的口语化表达转换为专业的技术术语。

            用户提问：%s

            请严格只返回纯JSON格式数据，不要包含任何 Markdown 标记或多余文字：
            {
                "rewritten_query": "使用专业术语后的查询",
                "terminology_changes": [{"original": "原词", "professional": "专业术语"}]
            }
            """;

    private static final String CONTEXT_COMPLETION_PROMPT = """
            你是一个上下文补全专家。请根据历史对话，将用户的简短提问补全为包含代词指代实体的完整查询句。

            历史对话：
            %s

            用户当前提问：%s

            请严格只返回纯JSON格式数据，不要包含任何 Markdown 标记或多余文字：
            {
                "completed_query": "补全后的完整查询",
                "context_used": "使用的上下文说明"
            }
            """;

    public record RewriteResult(
            String originalQuery,
            String rewrittenQuery,
            String reasoning,
            List<String> changes,
            boolean modified
    ) {}

    public RewriteResult rewriteQuery(String originalQuery, ContextWindow contextWindow, String userId) {
        String historyContext = buildHistoryContext(contextWindow);

        try {
            String prompt = REWRITE_PROMPT.formatted(historyContext, originalQuery);
            String response = chatModel.generate(prompt);

            String cleanResponse = JsonUtils.extractJson(response);
            com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(cleanResponse);

            String rewrittenQuery = jsonNode.get("rewritten_query").asText();
            String reasoning = jsonNode.has("reasoning") ? jsonNode.get("reasoning").asText() : "";

            List<String> changes = List.of();
            if (jsonNode.has("changes")) {
                com.fasterxml.jackson.databind.JsonNode changesNode = jsonNode.get("changes");
                changes = objectMapper.readerForListOf(String.class).readValue(changesNode);
            }

            boolean modified = !originalQuery.equals(rewrittenQuery);

            if (modified) {
                log.info("查询重写: '{}' -> '{}'", originalQuery, rewrittenQuery);
                logRewriteToDatabase(userId, originalQuery, rewrittenQuery, "hybrid", reasoning);
            }

            return new RewriteResult(originalQuery, rewrittenQuery, reasoning, changes, modified);

        } catch (Exception e) {
            log.error("查询重写失败", e);
            return new RewriteResult(originalQuery, originalQuery, "重写失败: " + e.getMessage(), List.of(), false);
        }
    }

    public RewriteResult terminologyRewrite(String originalQuery, String userId) {
        try {
            String prompt = TERMINOLOGY_PROMPT.formatted(originalQuery);
            String response = chatModel.generate(prompt);

            String cleanResponse = JsonUtils.extractJson(response);
            com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(cleanResponse);
            String rewrittenQuery = jsonNode.get("rewritten_query").asText();

            boolean modified = !originalQuery.equals(rewrittenQuery);

            if (modified) {
                log.info("术语规范化: '{}' -> '{}'", originalQuery, rewrittenQuery);
                logRewriteToDatabase(userId, originalQuery, rewrittenQuery, "terminology", "术语规范化");
            }

            return new RewriteResult(originalQuery, rewrittenQuery, "术语规范化", List.of(), modified);

        } catch (Exception e) {
            log.error("术语规范化失败", e);
            return new RewriteResult(originalQuery, originalQuery, "规范化失败: " + e.getMessage(), List.of(), false);
        }
    }

    public RewriteResult contextCompletionRewrite(String originalQuery, ContextWindow contextWindow, String userId) {
        String historyContext = buildHistoryContext(contextWindow);

        if (historyContext.isEmpty()) {
            return new RewriteResult(originalQuery, originalQuery, "无历史对话，不需要上下文补全", List.of(), false);
        }

        try {
            String prompt = CONTEXT_COMPLETION_PROMPT.formatted(historyContext, originalQuery);
            String response = chatModel.generate(prompt);

            String cleanResponse = JsonUtils.extractJson(response);
            com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(cleanResponse);
            String completedQuery = jsonNode.get("completed_query").asText();

            boolean modified = !originalQuery.equals(completedQuery);

            if (modified) {
                log.info("上下文补全: '{}' -> '{}'", originalQuery, completedQuery);
                logRewriteToDatabase(userId, originalQuery, completedQuery, "context_completion", "上下文补全");
            }

            return new RewriteResult(originalQuery, completedQuery, "上下文补全", List.of(), modified);

        } catch (Exception e) {
            log.error("上下文补全失败", e);
            return new RewriteResult(originalQuery, originalQuery, "补全失败: " + e.getMessage(), List.of(), false);
        }
    }

    public RewriteResult hybridRewrite(String originalQuery, ContextWindow contextWindow, String userId) {
        RewriteResult terminologyResult = terminologyRewrite(originalQuery, userId);
        String intermediateQuery = terminologyResult.rewrittenQuery();

        RewriteResult contextResult = contextCompletionRewrite(intermediateQuery, contextWindow, userId);

        if (terminologyResult.modified() || contextResult.modified()) {
            logRewriteToDatabase(userId, originalQuery, contextResult.rewrittenQuery(), "hybrid",
                    "术语规范化 + 上下文补全");
        }

        return new RewriteResult(originalQuery, contextResult.rewrittenQuery(),
                "术语规范化 + 上下文补全",
                List.of("术语规范化", "上下文补全"),
                !originalQuery.equals(contextResult.rewrittenQuery()));
    }

    private String buildHistoryContext(ContextWindow contextWindow) {
        StringBuilder sb = new StringBuilder();

        var shortTerm = contextWindow.getMemory().getShortTerm();

        if (shortTerm.getSummary() != null && !shortTerm.getSummary().isEmpty()) {
            sb.append("对话摘要: ").append(shortTerm.getSummary()).append("\n");
        }

        if (shortTerm.getHistory() != null) {
            for (var turn : shortTerm.getHistory()) {
                sb.append("第").append(turn.turn()).append("轮:\n");
                sb.append("  用户: ").append(turn.userQuery()).append("\n");
                sb.append("  AI: ").append(turn.aiResponse()).append("\n");
            }
        }

        return sb.toString();
    }

    private void logRewriteToDatabase(String userId, String originalQuery,
                                      String rewrittenQuery, String rewriteType, String reason) {
        try {
            QueryRewriteLog logEntry = new QueryRewriteLog();
            logEntry.setUserId(userId);
            logEntry.setOriginalQuery(originalQuery);
            logEntry.setRewrittenQuery(rewrittenQuery);
            logEntry.setRewriteType(rewriteType);
            logEntry.setRewriteReason(reason);
            rewriteLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("记录重写日志失败", e);
        }
    }
}