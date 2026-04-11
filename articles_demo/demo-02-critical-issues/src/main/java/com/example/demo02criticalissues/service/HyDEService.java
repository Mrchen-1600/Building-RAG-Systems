package com.example.demo02criticalissues.service;

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

import java.util.List;
import java.util.stream.Collectors;

/**
 *  ClassName: ConversationArchiveService
 *  Package: com.example.demo02criticalissues.service
 *
 *  @Author Mrchen
 *
 * HyDE (Hypothetical Document Embeddings) 服务
 * 通过生成假设性文档来增强检索效果
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HyDEService {

    private final ChatLanguageModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Value("${hyde.enabled:true}")
    private boolean hydeEnabled;

    @Value("${hyde.trigger-keywords:报告,文档,总结,分析,技术文档,撰写}")
    private List<String> triggerKeywords;

    private static final String HYPOTHETICAL_DOCUMENT_PROMPT = """
            你是一个知识专家。请基于以下问题，生成一段假设性的文档内容。
            这段文档不需要完全准确，但应该包含与问题相关的核心概念、专业术语和语义模式。

            问题：%s

            请生成一段详细的假设性文档内容（200-500字）。
            """;

    /**
     * HyDE检索结果
     */
    public record HyDEResult(
            boolean usedHyDE,
            String hypotheticalDocument,
            List<TextSegment> retrievedSegments
    ) {}

    /**
     * 判断是否需要使用HyDE
     *
     * @param userQuery 用户问题
     * @return true如果需要
     */
    public boolean shouldUseHyDE(String userQuery) {
        if (!hydeEnabled) {
            return false;
        }

        for (String keyword : triggerKeywords) {
            if (userQuery.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 执行HyDE检索
     *
     * @param userQuery 用户问题
     * @param topK     返回结果数量
     * @return HyDE检索结果
     */
    public HyDEResult executeHyDE(String userQuery, int topK) {
        // 1. 生成假设性文档
        String hypotheticalDoc = generateHypotheticalDocument(userQuery);
        log.info("生成的假设性文档: {}", hypotheticalDoc);

        // 2. 用假设性文档进行检索
        List<TextSegment> segments = retrieveWithHypotheticalDocument(hypotheticalDoc, topK);

        return new HyDEResult(true, hypotheticalDoc, segments);
    }

    /**
     * 生成假设性文档
     *
     * @param userQuery 用户问题
     * @return 假设性文档内容
     */
    private String generateHypotheticalDocument(String userQuery) {
        try {
            String prompt = HYPOTHETICAL_DOCUMENT_PROMPT.formatted(userQuery);
            String response = chatModel.generate(prompt);
            return response.trim();
        } catch (Exception e) {
            log.error("生成假设性文档失败", e);
            return userQuery; // 失败时使用原始查询
        }
    }

    /**
     * 使用假设性文档进行检索
     *
     * @param hypotheticalDocument 假设性文档
     * @param topK              返回结果数量
     * @return 检索到的文本段
     */
    private List<TextSegment> retrieveWithHypotheticalDocument(String hypotheticalDocument, int topK) {
        try {
            // 将假设性文档向量化
            Embedding embedding = embeddingModel.embed(hypotheticalDocument).content();

            // 执行检索
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
            log.error("使用假设性文档检索失败", e);
            return List.of();
        }
    }

    /**
     * 混合检索：先正常检索，如果结果不理想再用HyDE
     *
     * @param userQuery       用户问题
     * @param topK           返回结果数量
     * @param normalSegments  正常检索的结果
     * @param threshold      相似度阈值，低于此值使用HyDE
     * @return 最终检索结果
     */
    public List<TextSegment> hybridRetrieve(String userQuery, int topK,
                                         List<TextSegment> normalSegments, double threshold) {
        // 如果正常检索结果足够好（有足够的高相似度结果），直接返回
        if (normalSegments.size() >= topK && !normalSegments.isEmpty()) {
            log.info("正常检索结果良好，无需使用HyDE");
            return normalSegments;
        }

        // 否则使用HyDE
        log.info("正常检索结果不足，使用HyDE增强检索");
        HyDEResult hydeResult = executeHyDE(userQuery, topK);
        return hydeResult.retrievedSegments();
    }

    /**
     * 直接使用原始查询检索（不使用HyDE）
     *
     * @param userQuery 用户问题
     * @param topK     返回结果数量
     * @return 检索结果
     */
    public List<TextSegment> directRetrieve(String userQuery, int topK) {
        try {
            Embedding embedding = embeddingModel.embed(userQuery).content();

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
            log.error("直接检索失败", e);
            return List.of();
        }
    }

    /**
     * 智能检索：根据问题自动决定是否使用HyDE
     *
     * @param userQuery 用户问题
     * @param topK     返回结果数量
     * @return 检索结果
     */
    public List<TextSegment> smartRetrieve(String userQuery, int topK) {
        if (shouldUseHyDE(userQuery)) {
            log.info("问题包含HyDE触发关键词，使用HyDE检索");
            HyDEResult result = executeHyDE(userQuery, topK);
            return result.retrievedSegments();
        } else {
            log.info("问题不适合HyDE，使用直接检索");
            return directRetrieve(userQuery, topK);
        }
    }

    /**
     * 双路检索：同时使用原始查询和假设性文档检索，然后合并结果
     *
     * @param userQuery 用户问题
     * @param topK     返回结果数量
     * @return 合并后的检索结果
     */
    public List<TextSegment> dualRetrieve(String userQuery, int topK) {
        // 第一路：直接检索
        List<TextSegment> directResults = directRetrieve(userQuery, topK);

        // 第二路：HyDE检索
        HyDEResult hydeResult = executeHyDE(userQuery, topK);
        List<TextSegment> hydeResults = hydeResult.retrievedSegments();

        // 合并结果（去重）
        List<TextSegment> merged = new java.util.ArrayList<>(directResults);
        for (TextSegment segment : hydeResults) {
            if (!merged.contains(segment)) {
                merged.add(segment);
            }
        }

        // 限制返回数量
        return merged.stream().limit(topK).collect(Collectors.toList());
    }
}
