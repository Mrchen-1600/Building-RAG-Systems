package com.example.demo02criticalissues.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 *  ClassName: RaptorTree
 *  Package: com.example.demo02criticalissues.entity
 *
 *  @Author Mrchen
 */
@Data
@Entity
@Table(name = "raptor_tree", uniqueConstraints = {
    @UniqueConstraint(name = "uk_node_id", columnNames = {"node_id"})
})
public class RaptorTree {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_id", nullable = false, length = 64)
    private String docId;

    @Column(name = "node_id", nullable = false, length = 64, unique = true)
    private String nodeId;

    @Column(name = "level", nullable = false)
    private Integer level;

    @Column(name = "parent_node_id", length = 64)
    private String parentNodeId;

    @Column(name = "node_type", nullable = false, length = 50)
    private String nodeType;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "cluster_id", length = 64)
    private String clusterId;

    @Column(name = "child_node_ids", columnDefinition = "TEXT")
    private String childNodeIds;

    @Column(name = "embedding_vector_id", length = 64)
    private String embeddingVectorId;

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
