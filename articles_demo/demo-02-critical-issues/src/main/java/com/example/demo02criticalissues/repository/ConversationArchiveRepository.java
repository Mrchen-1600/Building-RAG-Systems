package com.example.demo02criticalissues.repository;

import com.example.demo02criticalissues.entity.ConversationArchive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话归档Repository
 */
@Repository
public interface ConversationArchiveRepository extends JpaRepository<ConversationArchive, Long> {

    /**
     * 根据用户ID和会话ID查询所有对话
     */
    List<ConversationArchive> findByUserIdAndSessionIdOrderByRoundNumAsc(String userId, String sessionId);

    /**
     * 查询会话的最近N轮对话
     */
    @Query("SELECT ca FROM ConversationArchive ca WHERE ca.userId = :userId AND ca.sessionId = :sessionId " +
           "ORDER BY ca.roundNum DESC")
    List<ConversationArchive> findRecentConversations(@Param("userId") String userId,
                                                        @Param("sessionId") String sessionId,
                                                        org.springframework.data.domain.Pageable pageable);

    /**
     * 根据会话ID查询最大轮次
     */
    @Query("SELECT MAX(ca.roundNum) FROM ConversationArchive ca WHERE ca.sessionId = :sessionId")
    Integer findMaxRoundNumBySessionId(@Param("sessionId") String sessionId);

    /**
     * 查询用户的所有会话ID
     */
    @Query("SELECT DISTINCT ca.sessionId FROM ConversationArchive ca WHERE ca.userId = :userId ORDER BY ca.createdAt DESC")
    List<String> findUserSessionIds(@Param("userId") String userId);

    /**
     * 根据时间范围查询对话
     */
    List<ConversationArchive> findByUserIdAndCreatedAtBetween(String userId, LocalDateTime start, LocalDateTime end);

    /**
     * 根据意图类型查询
     */
    List<ConversationArchive> findByUserIdAndIntentTypeOrderByCreatedAtDesc(String userId, String intentType);
}
