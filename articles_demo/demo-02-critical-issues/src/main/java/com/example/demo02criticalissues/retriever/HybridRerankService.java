package com.example.demo02criticalissues.retriever;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *  ClassName: HybridRerankService
 *  Package: com.example.demo02criticalissues.retriever
 *
 *  @Author Mrchen
 */
@Slf4j
@Service
public class HybridRerankService {

    private final EmbeddingStore<TextSegment> store;
    private final EmbeddingModel embeddingModel;
    private final Gson gson = new Gson();

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.rerank-model}")
    private String rerankModel;

    @Value("${dashscope.rerank-api-url}")
    private String rerankApiUrl;

    @Value("${retrieval.max-results:10}")
    private int maxResults;

    public HybridRerankService(
            EmbeddingStore<TextSegment> store,
            EmbeddingModel embeddingModel) {
        this.store = store;
        this.embeddingModel = embeddingModel;
    }

    public List<TextSegment> searchAndRerank(String query, int topK) {
        log.info("1. 执行向量召回 (粗筛 {} 条记录)...", maxResults);
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = store.search(searchRequest);
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

        List<TextSegment> initialSegments = matches.stream()
                .map(EmbeddingMatch::embedded)
                .collect(Collectors.toList());

        if (initialSegments.isEmpty()) {
            return initialSegments;
        }

        log.info("2. 执行 Cross-Encoder 重排序...");

        double[] scores = callDashScopeRerankAPI(query, initialSegments);

        return initialSegments.stream()
                .map(segment -> new ScoredSegment(segment, scores[initialSegments.indexOf(segment)]))
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(topK)
                .map(ss -> ss.segment)
                .collect(Collectors.toList());
    }

    private double[] callDashScopeRerankAPI(String query, List<TextSegment> segments) {
        List<String> documents = segments.stream().map(TextSegment::text).collect(Collectors.toList());
        double[] scores = new double[documents.size()];

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", rerankModel);

            Map<String, Object> input = new HashMap<>();
            input.put("query", query);
            input.put("documents", documents);
            requestBody.put("input", input);

            String jsonPayload = gson.toJson(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(rerankApiUrl))
                    .header("Authorization", "Bearer " + this.apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("重排序 API 调用失败, 状态码: {}, 响应: {}", response.statusCode(), response.body());
                return scores; // 如果调用失败，默认降级退回 0.0 打分，保留原有相对顺序
            }

            JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray results = jsonResponse.getAsJsonObject("output").getAsJsonArray("results");

            for (int i = 0; i < results.size(); i++) {
                JsonObject resultItem = results.get(i).getAsJsonObject();
                int originalIndex = resultItem.get("index").getAsInt();
                double relevanceScore = resultItem.get("relevance_score").getAsDouble();
                scores[originalIndex] = relevanceScore;
            }

        } catch (Exception e) {
            log.error("调用大模型重排序 API 时发生异常", e);
        }

        return scores;
    }

    private static class ScoredSegment {
        TextSegment segment;
        double score;
        ScoredSegment(TextSegment segment, double score) {
            this.segment = segment;
            this.score = score;
        }
    }
}