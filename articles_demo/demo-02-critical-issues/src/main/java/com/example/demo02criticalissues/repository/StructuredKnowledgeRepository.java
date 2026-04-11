package com.example.demo02criticalissues.repository;

import com.example.demo02criticalissues.entity.StructuredKnowledge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 结构化知识Repository
 */
@Repository
public interface StructuredKnowledgeRepository extends JpaRepository<StructuredKnowledge, Long> {

    /**
     * 根据知识类型查询
     */
    List<StructuredKnowledge> findByKnowledgeTypeAndIsActiveTrue(String knowledgeType);

    /**
     * 根据分类查询
     */
    List<StructuredKnowledge> findByCategoryAndIsActiveTrue(String category);

    /**
     * 根据标签查询（模糊匹配）
     */
    @Query("SELECT sk FROM StructuredKnowledge sk WHERE sk.isActive = true AND sk.tags LIKE %:tag%")
    List<StructuredKnowledge> findByTagContaining(@Param("tag") String tag);

    /**
     * 根据标题或内容模糊查询
     */
    @Query("SELECT sk FROM StructuredKnowledge sk WHERE sk.isActive = true AND " +
           "(sk.title LIKE %:keyword% OR sk.content LIKE %:keyword%)")
    List<StructuredKnowledge> findByKeyword(@Param("keyword") String keyword);

    /**
     * 根据优先级排序查询
     */
    List<StructuredKnowledge> findByIsActiveTrueOrderByPriorityDesc();

    /**
     * 根据知识类型和优先级查询
     */
    List<StructuredKnowledge> findByKnowledgeTypeAndIsActiveTrueOrderByPriorityDesc(String knowledgeType);
}
