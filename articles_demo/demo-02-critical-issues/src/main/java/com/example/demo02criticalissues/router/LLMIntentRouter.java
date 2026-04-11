package com.example.demo02criticalissues.router;

import com.example.demo02criticalissues.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 *  ClassName: LLMIntentRouter
 *  Package: com.example.demo02criticalissues.router
 *
 *  @Author Mrchen
 *
 * 大模型逻辑路由
 * 通过分析用户问题，判断应该使用哪种处理方式
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMIntentRouter {

    private final ChatLanguageModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${intent-routing.enable-llm-router:true}")
    private boolean enableLLMRouter;

    /**
     * 意图类型枚举
     */
    @Getter
    public enum IntentType {
        STRUCTURED_QUERY("结构化知识查询", "需要查询结构化数据，使用SQL查询"),
        VECTOR_SEARCH("向量库检索", "需要在知识库中搜索相关信息"),
        COMPARISON_RETRIEVAL("对比检索", "需要对比多个文档或信息源，使用迭代检索"),
        SUMMARY_QUERY("总结查询", "需要总结大段内容或全局概览，使用RAPTOR"),
        COMPLEX_TASK("复杂任务", "需要撰写报告、文档等复杂内容，使用HyDE"),
        CHAT("闲聊", "普通对话，不需要检索任何内容");

        private final String displayName;
        private final String description;

        IntentType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    /**
     * 路由结果
     */
    public record RouteResult(
            IntentType intentType,
            String reasoning,
            String rewrittenQuery
    ) {}

    private static final String ROUTER_PROMPT = """
            你是一个意图识别专家。请分析用户的问题，判断最适合的处理方式。

            可用的处理方式：

            1. STRUCTURED_QUERY（结构化知识查询）
               - 适用场景：查询具体的数据、统计信息、记录等
               - 示例："查询有多少篇技术文档"、"查找用户角色为开发者的用户"

            2. VECTOR_SEARCH（向量库检索）
               - 适用场景：需要在知识库中搜索相关技术文档、政策等信息
               - 示例："RAG技术是什么"、"差旅报销标准是什么"

            3. COMPARISON_RETRIEVAL（对比检索）
               - 适用场景：需要对比多个文档或信息源
               - 示例："对比一下RAG技术和GraphRAG的区别"、"比较不同技术的优劣"

            4. SUMMARY_QUERY（总结查询）
               - 适用场景：需要总结大段内容或全局概览
               - 示例："总结一下RAG技术的发展历程"、"概述一下整个系统的架构"

            5. COMPLEX_TASK（复杂任务）
               - 适用场景：需要撰写报告、技术文档等复杂内容
               - 示例："帮我写一份关于RAG技术的报告"、"生成一份技术文档"

            6. CHAT（闲聊）
               - 适用场景：普通对话，打招呼等
               - 示例："你好"、"天气怎么样"

            请严格只返回纯JSON格式数据，不要包含任何 Markdown 标记或多余文字：
            {
                "intent_type": "INTENT_TYPE",
                "reasoning": "判断理由",
                "rewritten_query": "改写后的查询（如果需要）"
            }

            用户问题：%s
            """;

    /**
     * 分析用户意图
     *
     * @param userQuery 用户问题
     * @return 路由结果
     */
    public RouteResult route(String userQuery) {
        if (!enableLLMRouter) {
            return new RouteResult(IntentType.VECTOR_SEARCH, "未启用大模型路由，使用默认向量检索", userQuery);
        }

        try {
            String prompt = ROUTER_PROMPT.formatted(userQuery);
            String response = chatModel.generate(prompt);

            String cleanResponse = JsonUtils.extractJson(response);
            JsonNode jsonNode = objectMapper.readTree(cleanResponse);

            String intentTypeStr = jsonNode.get("intent_type").asText();
            String reasoning = jsonNode.get("reasoning").asText();
            String rewrittenQuery = jsonNode.has("rewritten_query") &&
                    !jsonNode.get("rewritten_query").isNull()
                    ? jsonNode.get("rewritten_query").asText()
                    : userQuery;

            IntentType intentType = IntentType.valueOf(intentTypeStr);

            log.info("大模型路由结果: {} - {}", intentType, reasoning);

            return new RouteResult(intentType, reasoning, rewrittenQuery);

        } catch (Exception e) {
            log.error("大模型路由失败，降级使用默认路由", e);
            return new RouteResult(IntentType.VECTOR_SEARCH, "路由识别异常，默认降级为向量检索", userQuery);
        }
    }
}