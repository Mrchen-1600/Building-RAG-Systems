package com.example.demo02criticalissues.router;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *  ClassName: SemanticIntentRouter
 *  Package: com.example.demo02criticalissues.router
 *
 *  @Author Mrchen
 *
 * 语义路由
 * 通过计算用户问题与预设主题向量的相似度来决定路由
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticIntentRouter {

    private final EmbeddingModel embeddingModel;

    @Value("${intent-routing.enable-semantic-router:false}")
    private boolean enableSemanticRouter;

    @Value("${intent-routing.semantic-threshold:0.7}")
    private double semanticThreshold;

    /**
     * 主题定义
     */
    private static final Map<String, List<String>> TOPIC_SENTENCES = Map.of(
            LLMIntentRouter.IntentType.STRUCTURED_QUERY.name(), List.of(
                    "查询数据库中的数据",
                    "统计记录数量",
                    "查找具体的信息记录",
                    "按条件筛选数据"
            ),
            LLMIntentRouter.IntentType.VECTOR_SEARCH.name(), List.of(
                    "搜索技术文档内容",
                    "查找相关知识",
                    "检索政策规定",
                    "查询文档中的信息"
            ),
            LLMIntentRouter.IntentType.COMPARISON_RETRIEVAL.name(), List.of(
                    "对比不同的技术方案",
                    "比较各种方法的优劣",
                    "分析不同文档的区别"
            ),
            LLMIntentRouter.IntentType.SUMMARY_QUERY.name(), List.of(
                    "总结文档的主要内容",
                    "概括技术发展历程",
                    "归纳系统的整体架构",
                    "全局概览相关信息"
            ),
            LLMIntentRouter.IntentType.COMPLEX_TASK.name(), List.of(
                    "撰写技术报告",
                    "生成文档",
                    "创建分析材料",
                    "起草总结文档"
            ),
            LLMIntentRouter.IntentType.CHAT.name(), List.of(
                    "日常聊天问候",
                    "天气咨询",
                    "简单对话交流",
                    "礼貌性用语"
            )
    );

    // 使用并发安全的 Map 缓存向量，防止多请求下引发异常
    private final Map<String, List<Embedding>> topicEmbeddingsCache = new ConcurrentHashMap<>();

    public record SemanticRouteResult(
            LLMIntentRouter.IntentType intentType,
            double similarityScore,
            String reasoning
    ) {}

    /**
     * 初始化时预计算主题向量
     */
    public void init() {
        try {
            for (Map.Entry<String, List<String>> entry : TOPIC_SENTENCES.entrySet()) {
                String topic = entry.getKey();
                List<String> sentences = entry.getValue();

                List<Embedding> embeddings = sentences.stream()
                        .map(embeddingModel::embed)
                        .map(Response::content)
                        .toList();

                topicEmbeddingsCache.put(topic, embeddings);
            }
            log.info("语义路由初始化完成，已预计算 {} 个主题的向量", topicEmbeddingsCache.size());
        } catch (Exception e) {
            log.error("语义路由初始化失败", e);
        }
    }

    /**
     * 分析用户意图（使用语义路由）
     *
     * @param userQuery 用户问题
     * @return 路由结果
     */
    public SemanticRouteResult route(String userQuery) {
        if (!enableSemanticRouter) {
            return new SemanticRouteResult(
                    LLMIntentRouter.IntentType.VECTOR_SEARCH,
                    0.0,
                    "未启用语义路由，使用默认向量检索"
            );
        }

        // 如果缓存未初始化，先初始化
        if (topicEmbeddingsCache == null) {
            init();
        }

        // 将用户问题转换为向量
        Embedding queryEmbedding = embeddingModel.embed(userQuery).content();

        // 计算与每个主题的相似度
        String bestMatchTopic = null;
        double bestSimilarity = 0.0;

        for (Map.Entry<String, List<Embedding>> entry : topicEmbeddingsCache.entrySet()) {
            String topic = entry.getKey();
            List<Embedding> embeddings = entry.getValue();

            // 计算与该主题下所有句子的最大相似度
            double maxSimilarityForTopic = embeddings.stream()
                    .mapToDouble(embedding -> cosineSimilarity(queryEmbedding, embedding))
                    .max()
                    .orElse(0.0);

            if (maxSimilarityForTopic > bestSimilarity) {
                bestSimilarity = maxSimilarityForTopic;
                bestMatchTopic = topic;
            }
        }

        // 检查是否达到阈值
        if (bestSimilarity < semanticThreshold) {
            log.warn("语义路由相似度 {} 低于阈值 {}，使用默认路由", bestSimilarity, semanticThreshold);
            return new SemanticRouteResult(
                    LLMIntentRouter.IntentType.VECTOR_SEARCH,
                    bestSimilarity,
                    String.format("相似度 %.2f 低于阈值 %.2f，使用默认向量检索", bestSimilarity, semanticThreshold)
            );
        }

        LLMIntentRouter.IntentType intentType = LLMIntentRouter.IntentType.valueOf(bestMatchTopic);
        log.info("语义路由结果: {} (相似度: {})", intentType, bestSimilarity);

        return new SemanticRouteResult(
                intentType,
                bestSimilarity,
                String.format("语义相似度为 %.2f，匹配到主题: %s", bestSimilarity, intentType.getDisplayName())
        );
    }

    /**
     * 计算余弦相似度
     *
     * @param v1 向量1
     * @param v2 向量2
     * @return 相似度（0-1之间）
     */
    private double cosineSimilarity(Embedding v1, Embedding v2) {
        List<Float> vec1 = v1.vectorAsList();
        List<Float> vec2 = v2.vectorAsList();

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.size(); i++) {
            dotProduct += vec1.get(i) * vec2.get(i);
            norm1 += Math.pow(vec1.get(i), 2);
            norm2 += Math.pow(vec2.get(i), 2);
        }
        if (norm1 == 0 || norm2 == 0) return 0.0;
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
