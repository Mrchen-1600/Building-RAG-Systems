package com.example.demo02criticalissues.embedding;

import dev.langchain4j.model.dashscope.QwenEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ClassName: EmbeddingModelConfig
 * Package: com.example.demo02criticalissues.embedding
 *
 * @Author Mrchen
 */
@Configuration
public class EmbeddingModelConfig {

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.embedding-model}")
    private String embeddingModelName;

    @Bean
    public dev.langchain4j.model.embedding.EmbeddingModel embeddingModel() {
        return QwenEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(embeddingModelName)
                .build();
    }
}
