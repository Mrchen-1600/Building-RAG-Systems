package com.example.demo02criticalissues.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 *  ClassName: SentenceMetadata
 *  Package: com.example.demo02criticalissues.entity
 *
 *  @Author Mrchen
 */
@Data
@Entity
@Table(name = "sentence_metadata", uniqueConstraints = {
    @UniqueConstraint(name = "uk_doc_sentence", columnNames = {"doc_id", "sentence_index"})
})
public class SentenceMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_id", nullable = false, length = 64)
    private String docId;

    @Column(name = "sentence_index", nullable = false)
    private Integer sentenceIndex;

    @Column(name = "sentence_text", nullable = false, columnDefinition = "TEXT")
    private String sentenceText;

    @Column(name = "sentence_vector_id", length = 64)
    private String sentenceVectorId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
