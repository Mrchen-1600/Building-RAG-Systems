package com.example.demo02criticalissues.chat;

import dev.langchain4j.model.dashscope.QwenChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ClassName: ChatModelConfig
 * Package: com.example.demo02criticalissues.chat
 *
 * @Author Mrchen
 */
@Configuration
public class ChatModelConfig {

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.chat-model}")
    private String chatModelName;

    @Bean
    public QwenChatModel qwenChatModel() {
        return QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName(chatModelName)
                .build();
    }
}
