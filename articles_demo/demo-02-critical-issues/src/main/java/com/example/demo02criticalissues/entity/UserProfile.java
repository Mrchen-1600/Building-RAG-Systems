package com.example.demo02criticalissues.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 *  ClassName: UserProfile
 *  Package: com.example.demo02criticalissues.entity
 *
 *  @Author Mrchen
 */
@Data
@Entity
@Table(name = "user_profile", uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_version", columnNames = {"user_id", "version"})
})
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "user_role", length = 100)
    private String userRole;

    @Column(name = "preferences", columnDefinition = "TEXT")
    private String preferences;

    @Column(name = "interests", columnDefinition = "TEXT")
    private String interests;

    @Column(name = "expertise_level", length = 50)
    private String expertiseLevel;

    @Column(name = "frequently_asked_topics", columnDefinition = "TEXT")
    private String frequentlyAskedTopics;

    @Column(name = "learning_style", length = 50)
    private String learningStyle;

    @Column(name = "interaction_pattern", length = 50)
    private String interactionPattern;

    @Column(name = "additional_info", columnDefinition = "TEXT")
    private String additionalInfo;

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
