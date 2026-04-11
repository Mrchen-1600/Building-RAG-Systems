package com.example.demo02criticalissues.repository;

import com.example.demo02criticalissues.entity.SelfRagEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Self-RAG评估Repository
 */
@Repository
public interface SelfRagEvaluationRepository extends JpaRepository<SelfRagEvaluation, Long> {

    /**
     * 根据对话ID查询所有评估
     */
    List<SelfRagEvaluation> findByConversationId(Long conversationId);

    /**
     * 根据对话ID和评估类型查询
     */
    List<SelfRagEvaluation> findByConversationIdAndEvaluationType(Long conversationId, String evaluationType);

    /**
     * 根据评估结果查询
     */
    List<SelfRagEvaluation> findByEvaluationResult(String evaluationResult);
}
