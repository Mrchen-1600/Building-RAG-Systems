package com.example.demo02criticalissues.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 *  ClassName: ConversationArchive
 *  Package: com.example.demo02criticalissues.entity
 *
 *  @Author Mrchen
 */
@Data
@Entity
@Table(name = "conversation_archive")
public class ConversationArchive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "round_num", nullable = false)
    private Integer roundNum;

    @Column(name = "user_query", nullable = false, columnDefinition = "TEXT")
    private String userQuery;

    @Column(name = "rewritten_query", columnDefinition = "TEXT")
    private String rewrittenQuery;

    @Column(name = "retrieved_context", columnDefinition = "TEXT")
    private String retrievedContext;

    @Column(name = "ai_response", columnDefinition = "TEXT")
    private String aiResponse;

    @Column(name = "intent_type", length = 50)
    private String intentType;

    @Column(name = "retrieval_strategy", length = 50)
    private String retrievalStrategy;

    @Column(name = "used_tools", columnDefinition = "TEXT")
    private String usedTools;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "satisfaction_score")
    private Integer satisfactionScore;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
