package com.example.demo01naiverag.store;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ESStoreService {

    @Value("${elasticsearch.host}")
    private String host;

    @Value("${elasticsearch.port}")
    private int port;

    @Value("${elasticsearch.scheme}")
    private String scheme;

    @Value("${elasticsearch.index-name}")
    private String indexName;

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        try {
            RestClient restClient = RestClient.builder(
                    new HttpHost(host, port, scheme)
            ).build();

            return ElasticsearchEmbeddingStore.builder()
                    .restClient(restClient)
                    .indexName(indexName)
                    .build();

        } catch (Exception e) {
            System.err.println("无法连接到 Elasticsearch，已自动降级为【内存向量库】以便测试运行: " + e.getMessage());
            return new InMemoryEmbeddingStore<>();
        }
    }
}
