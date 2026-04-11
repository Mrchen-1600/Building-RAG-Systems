package com.example.demo02criticalissues.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 *  ClassName: StructuredKnowledge
 *  Package: com.example.demo02criticalissues.entity
 *
 *  @Author Mrchen
 */
@Data
@Entity
@Table(name = "structured_knowledge")
public class StructuredKnowledge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "knowledge_type", nullable = false, length = 50)
    private String knowledgeType;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "tags", length = 500)
    private String tags;

    @Column(name = "source")
    private String source;

    @Column(name = "priority")
    private Integer priority = 0;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
