package com.example.demo02criticalissues.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 *  ClassName: ParentChildChunk
 *  Package: com.example.demo02criticalissues.entity
 *
 *  @Author Mrchen
 */
@Data
@Entity
@Table(name = "parent_child_chunk")
public class ParentChildChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_id", nullable = false, length = 64)
    private String docId;

    @Column(name = "parent_id", nullable = false, length = 64)
    private String parentId;

    @Column(name = "parent_text", nullable = false, columnDefinition = "TEXT")
    private String parentText;

    @Column(name = "child_id", nullable = false, length = 64)
    private String childId;

    @Column(name = "child_text", nullable = false, columnDefinition = "TEXT")
    private String childText;

    @Column(name = "child_vector_id", length = 64)
    private String childVectorId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
