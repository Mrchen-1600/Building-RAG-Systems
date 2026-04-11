package com.example.demo02criticalissues.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 *  ClassName: DocumentMetadata
 *  Package: com.example.demo02criticalissues.entity
 *
 *  @Author Mrchen
 */
@Data
@Entity
@Table(name = "document_metadata", uniqueConstraints = {
    @UniqueConstraint(name = "uk_doc_id", columnNames = {"doc_id"})
})
public class DocumentMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_id", nullable = false, length = 64, unique = true)
    private String docId;

    @Column(name = "doc_name", nullable = false)
    private String docName;

    @Column(name = "doc_path", length = 500)
    private String docPath;

    @Column(name = "doc_type", length = 50)
    private String docType;

    @Column(name = "total_sentences")
    private Integer totalSentences;

    @Column(name = "total_chunks")
    private Integer totalChunks;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
