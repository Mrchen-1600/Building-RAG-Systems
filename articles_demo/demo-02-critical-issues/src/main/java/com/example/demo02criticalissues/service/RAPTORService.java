package com.example.demo02criticalissues.service;

import com.example.demo02criticalissues.entity.RaptorTree;
import com.example.demo02criticalissues.repository.RaptorTreeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 *  ClassName: RAPTORService
 *  Package: com.example.demo02criticalissues.service
 *
 *  @Author Mrchen
 *
 * RAPTOR (Recursive Abstractive Processing for Tree-Organized Retrieval) 服务
 * 树状摘要检索，支持层次化摘要查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RAPTORService {

    private final ChatLanguageModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final RaptorTreeRepository raptorTreeRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${raptor.enabled:true}")
    private boolean raptorEnabled;

    @Value("${raptor.max-levels:5}")
    private int maxLevels;

    @Value("${retrieval.top-k:5}")
    private int topK;

    private static final String CLUSTERING_PROMPT = """
            你是一个文本聚类专家。请将以下文本块按语义相似度分组。

            文本块：
            %s

            请以JSON格式返回聚类结果：
            {
                "clusters": [
                    {
                        "cluster_id": "cluster_1",
                        "chunk_ids": ["id1", "id2"],
                        "summary": "该聚类的摘要"
                    }
                ]
            }
            """;

    private static final String SUMMARY_PROMPT = """
            你是一个文本摘要专家。请为以下文本生成一段简洁的摘要（200字以内）。

            文本内容：
            %s

            请生成摘要：
            """;

    public record RAPTORResult(
            List<String> summaries,
            List<RaptorTree> nodes,
            int level
    ) {}

    public boolean isSuitableForRAPTOR(String userQuery) {
        if (!raptorEnabled) {
            return false;
        }

        String[] keywords = {"总结", "概述", "汇总", "归纳", "全局", "整体", "全面", "概览",
                "核心观点", "主要内容", "整体架构", "发展历程"};
        for (String keyword : keywords) {
            if (userQuery.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public RAPTORResult retrieve(String docId, String userQuery) {
        Optional<Integer> maxLevelOpt = raptorTreeRepository.findMaxLevelByDocId(docId);
        if (maxLevelOpt.isEmpty()) {
            log.warn("文档 {} 没有RAPTOR树，使用普通检索", docId);
            return new RAPTORResult(List.of(), List.of(), 0);
        }

        int maxLevel = maxLevelOpt.get();
        int targetLevel = determineTargetLevel(userQuery, maxLevel);

        log.info("RAPTOR检索 - 文档: {}, 目标层级: {}, 最大层级: {}", docId, targetLevel, maxLevel);

        List<RaptorTree> nodes = raptorTreeRepository.findByDocIdAndLevelOrderByNodeId(docId, targetLevel);

        List<RaptorTree> relevantNodes = findRelevantNodes(nodes, userQuery);

        List<String> summaries = relevantNodes.stream()
                .map(RaptorTree::getSummary)
                .filter(Objects::nonNull)
                .toList();

        return new RAPTORResult(summaries, relevantNodes, targetLevel);
    }

    public Optional<String> getRootSummary(String docId) {
        return raptorTreeRepository.findRootNodeByDocId(docId)
                .map(RaptorTree::getSummary);
    }

    public List<String> getSummariesByLevel(String docId, int level) {
        return raptorTreeRepository.findByDocIdAndLevelOrderByNodeId(docId, level).stream()
                .map(RaptorTree::getSummary)
                .filter(Objects::nonNull)
                .toList();
    }

    private int determineTargetLevel(String userQuery, int maxLevel) {
        String[] globalKeywords = {"整体", "全局", "完整", "全面", "所有", "全部"};
        for (String keyword : globalKeywords) {
            if (userQuery.contains(keyword)) {
                return maxLevel;
            }
        }
        String[] summaryKeywords = {"总结", "概述", "归纳", "汇总"};
        for (String keyword : summaryKeywords) {
            if (userQuery.contains(keyword)) {
                return Math.max(maxLevel - 1, 1);
            }
        }
        return maxLevel / 2;
    }

    private List<RaptorTree> findRelevantNodes(List<RaptorTree> nodes, String userQuery) {
        try {
            List<RaptorTree> validNodes = nodes.stream()
                    .filter(n -> n.getSummary() != null && !n.getSummary().isEmpty())
                    .toList();

            if (validNodes.isEmpty()) {
                return List.of();
            }

            Embedding queryEmbedding = embeddingModel.embed(userQuery).content();

            List<TextSegment> summarySegments = validNodes.stream()
                    .map(n -> TextSegment.from(n.getSummary()))
                    .toList();

            List<Embedding> nodeEmbeddings = embeddingModel.embedAll(summarySegments).content();

            List<Map.Entry<RaptorTree, Double>> similarityScores = new ArrayList<>();
            for (int i = 0; i < validNodes.size(); i++) {
                double similarity = cosineSimilarity(queryEmbedding, nodeEmbeddings.get(i));
                similarityScores.add(new AbstractMap.SimpleEntry<>(validNodes.get(i), similarity));
            }

            return similarityScores.stream()
                    .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                    .limit(topK)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("查找相关节点失败", e);
            return nodes.stream().limit(topK).collect(Collectors.toList());
        }
    }

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

    @Async
    public void buildRaptorTreeAsync(String docId, List<String> chunkTexts, List<String> chunkIds) {
        try {
            log.info("开始异步构建RAPTOR树 - 文档: {}", docId);
            List<RaptorTree> oldTrees = raptorTreeRepository.findByDocIdOrderByLevelAscNodeId(docId);
            raptorTreeRepository.deleteAll(oldTrees);

            List<RaptorTree> currentLevelNodes = createLeafNodes(docId, chunkTexts, chunkIds);
            raptorTreeRepository.saveAll(currentLevelNodes);

            int level = 1;
            while (level < maxLevels && currentLevelNodes.size() > 1) {
                List<RaptorTree> nextLevelNodes = clusterAndSummarize(docId, currentLevelNodes, level);
                if (nextLevelNodes.isEmpty()) break;

                raptorTreeRepository.saveAll(nextLevelNodes);
                currentLevelNodes = nextLevelNodes;
                level++;
                log.info("RAPTOR树构建 - 文档: {}, 层级: {}, 节点数: {}", docId, level, currentLevelNodes.size());
            }

            if (!currentLevelNodes.isEmpty()) {
                RaptorTree rootNode = createRootNode(docId, currentLevelNodes, level);
                raptorTreeRepository.save(rootNode);
                log.info("RAPTOR树构建完成 - 文档: {}, 最大层级: {}", docId, level);
            }
        } catch (Exception e) {
            log.error("构建RAPTOR树失败", e);
        }
    }

    private List<RaptorTree> createLeafNodes(String docId, List<String> chunkTexts, List<String> chunkIds) {
        List<RaptorTree> nodes = new ArrayList<>();
        for (int i = 0; i < chunkTexts.size(); i++) {
            RaptorTree node = new RaptorTree();
            node.setDocId(docId);
            node.setNodeId("leaf_" + docId + "_" + i);
            node.setLevel(0);
            node.setNodeType("leaf");
            node.setContent(chunkTexts.get(i));
            node.setClusterId("cluster_0_" + i);
            nodes.add(node);
        }
        return nodes;
    }

    private List<RaptorTree> clusterAndSummarize(String docId, List<RaptorTree> childNodes, int level) {
        List<RaptorTree> parentNodes = new ArrayList<>();
        int clusterSize = 4;
        for (int i = 0; i < childNodes.size(); i += clusterSize) {
            int endIndex = Math.min(i + clusterSize, childNodes.size());
            List<RaptorTree> cluster = childNodes.subList(i, endIndex);

            String clusterId = UUID.randomUUID().toString();
            String summary = generateClusterSummary(cluster);

            RaptorTree parentNode = new RaptorTree();
            parentNode.setDocId(docId);
            parentNode.setNodeId("node_" + docId + "_" + level + "_" + parentNodes.size());
            parentNode.setLevel(level);
            parentNode.setNodeType("summary");
            parentNode.setSummary(summary);
            parentNode.setClusterId(clusterId);
            parentNode.setChildNodeIds(cluster.stream().map(RaptorTree::getNodeId).collect(Collectors.joining(",")));

            parentNodes.add(parentNode);
        }
        return parentNodes;
    }

    private String generateClusterSummary(List<RaptorTree> nodes) {
        String content = nodes.stream()
                .map(RaptorTree::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
        try {
            String prompt = SUMMARY_PROMPT.formatted(content);
            return chatModel.generate(prompt).trim();
        } catch (Exception e) {
            log.error("生成聚类摘要失败", e);
            return "摘要生成失败";
        }
    }

    private RaptorTree createRootNode(String docId, List<RaptorTree> childNodes, int level) {
        String summary = generateClusterSummary(childNodes);
        RaptorTree rootNode = new RaptorTree();
        rootNode.setDocId(docId);
        rootNode.setNodeId("root_" + docId);
        rootNode.setLevel(level);
        rootNode.setNodeType("root");
        rootNode.setSummary(summary);
        rootNode.setClusterId("root_cluster");
        rootNode.setChildNodeIds(childNodes.stream().map(RaptorTree::getNodeId).collect(Collectors.joining(",")));
        return rootNode;
    }
}