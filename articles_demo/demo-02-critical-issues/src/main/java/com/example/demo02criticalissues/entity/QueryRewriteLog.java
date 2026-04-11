package com.example.demo02criticalissues.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 *  ClassName: QueryRewriteLog
 *  Package: com.example.demo02criticalissues.entity
 *
 *  @Author Mrchen
 */
@Data
@Entity
@Table(name = "query_rewrite_log")
public class QueryRewriteLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "original_query", nullable = false, columnDefinition = "TEXT")
    private String originalQuery;

    @Column(name = "rewritten_query", nullable = false, columnDefinition = "TEXT")
    private String rewrittenQuery;

    @Column(name = "rewrite_type", length = 50)
    private String rewriteType;

    @Column(name = "rewrite_reason", columnDefinition = "TEXT")
    private String rewriteReason;

    @Column(name = "similarity_score")
    private Double similarityScore;

    @Column(name = "context_used", columnDefinition = "TEXT")
    private String contextUsed;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
