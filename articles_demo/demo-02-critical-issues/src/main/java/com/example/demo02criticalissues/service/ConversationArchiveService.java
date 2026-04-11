package com.example.demo02criticalissues.service;

import com.example.demo02criticalissues.entity.ConversationArchive;
import com.example.demo02criticalissues.repository.ConversationArchiveRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 *  ClassName: ConversationArchiveService
 *  Package: com.example.demo02criticalissues.service
 *
 *  @Author Mrchen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationArchiveService {

    private final ConversationArchiveRepository conversationArchiveRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 归档对话记录
     *
     * @param userId             用户ID
     * @param sessionId          会话ID
     * @param roundNum           轮次号
     * @param userQuery          用户提问
     * @param rewrittenQuery     改写后的查询
     * @param retrievedContext   检索到的上下文
     * @param aiResponse         AI回复
     * @param intentType         意图类型
     * @param retrievalStrategy  检索策略
     * @param usedTools          使用的工具
     * @param responseTimeMs     响应时间
     */
    @Transactional
    public void archiveConversation(String userId,
                                   String sessionId,
                                   Integer roundNum,
                                   String userQuery,
                                   String rewrittenQuery,
                                   String retrievedContext,
                                   String aiResponse,
                                   String intentType,
                                   String retrievalStrategy,
                                   List<String> usedTools,
                                   Integer responseTimeMs) {
        ConversationArchive archive = new ConversationArchive();
        archive.setUserId(userId);
        archive.setSessionId(sessionId);
        archive.setRoundNum(roundNum);
        archive.setUserQuery(userQuery);
        archive.setRewrittenQuery(rewrittenQuery);
        archive.setRetrievedContext(retrievedContext);
        archive.setAiResponse(aiResponse);
        archive.setIntentType(intentType);
        archive.setRetrievalStrategy(retrievalStrategy);
        archive.setResponseTimeMs(responseTimeMs);

        // 序列化使用的工具列表
        if (usedTools != null && !usedTools.isEmpty()) {
            try {
                archive.setUsedTools(objectMapper.writeValueAsString(usedTools));
            } catch (JsonProcessingException e) {
                log.error("JSON序列化失败", e);
            }
        }

        conversationArchiveRepository.save(archive);
        log.info("已归档对话记录 - 用户: {}, 会话: {}, 轮次: {}", userId, sessionId, roundNum);
    }

    /**
     * 获取会话的所有对话历史
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @return 对话历史列表
     */
    public List<ConversationArchive> getConversationHistory(String userId, String sessionId) {
        return conversationArchiveRepository.findByUserIdAndSessionIdOrderByRoundNumAsc(userId, sessionId);
    }

    /**
     * 获取最近的N轮对话
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @param limit     限制数量
     * @return 对话历史列表
     */
    public List<ConversationArchive> getRecentConversations(String userId, String sessionId, int limit) {
        return conversationArchiveRepository.findRecentConversations(userId, sessionId, PageRequest.of(0, limit));
    }

    /**
     * 获取会话的下一轮次号
     *
     * @param sessionId 会话ID
     * @return 下一轮次号
     */
    @Transactional
    public Integer getNextRoundNum(String sessionId) {
        Integer maxRound = conversationArchiveRepository.findMaxRoundNumBySessionId(sessionId);
        return maxRound == null ? 1 : maxRound + 1;
    }

    /**
     * 获取用户的所有会话ID
     *
     * @param userId 用户ID
     * @return 会话ID列表
     */
    public List<String> getUserSessionIds(String userId) {
        return conversationArchiveRepository.findUserSessionIds(userId);
    }

    /**
     * 记录用户反馈
     *
     * @param conversationId 对话记录ID
     * @param satisfactionScore 满意度评分
     * @param feedback        反馈内容
     */
    @Transactional
    public void recordFeedback(Long conversationId, Integer satisfactionScore, String feedback) {
        conversationArchiveRepository.findById(conversationId).ifPresent(archive -> {
            archive.setSatisfactionScore(satisfactionScore);
            archive.setFeedback(feedback);
            conversationArchiveRepository.save(archive);
            log.info("已记录用户反馈 - 对话ID: {}, 评分: {}", conversationId, satisfactionScore);
        });
    }

    /**
     * 根据意图类型查询对话
     *
     * @param userId     用户ID
     * @param intentType 意图类型
     * @return 对话列表
     */
    public List<ConversationArchive> getConversationsByIntent(String userId, String intentType) {
        return conversationArchiveRepository.findByUserIdAndIntentTypeOrderByCreatedAtDesc(userId, intentType);
    }
}
