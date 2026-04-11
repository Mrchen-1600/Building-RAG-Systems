package com.example.demo02criticalissues.repository;

import com.example.demo02criticalissues.entity.SentenceMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 句子元数据Repository
 */
@Repository
public interface SentenceMetadataRepository extends JpaRepository<SentenceMetadata, Long> {

    /**
     * 根据文档ID和句子索引查询
     */
    Optional<SentenceMetadata> findByDocIdAndSentenceIndex(String docId, Integer sentenceIndex);

    /**
     * 根据文档ID查询所有句子
     */
    List<SentenceMetadata> findByDocIdOrderBySentenceIndexAsc(String docId);

    /**
     * 根据文档ID和索引范围查询（用于句子窗口检索）
     */
    @Query("SELECT sm FROM SentenceMetadata sm WHERE sm.docId = :docId AND " +
           "sm.sentenceIndex BETWEEN :startIndex AND :endIndex ORDER BY sm.sentenceIndex ASC")
    List<SentenceMetadata> findByDocIdAndSentenceIndexRange(@Param("docId") String docId,
                                                              @Param("startIndex") Integer startIndex,
                                                              @Param("endIndex") Integer endIndex);

    /**
     * 根据向量库ID查询
     */
    Optional<SentenceMetadata> findBySentenceVectorId(String sentenceVectorId);
}
