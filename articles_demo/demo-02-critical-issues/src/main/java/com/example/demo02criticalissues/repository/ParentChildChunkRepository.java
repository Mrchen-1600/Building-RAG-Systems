package com.example.demo02criticalissues.repository;

import com.example.demo02criticalissues.entity.ParentChildChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 父子块映射Repository
 */
@Repository
public interface ParentChildChunkRepository extends JpaRepository<ParentChildChunk, Long> {

    /**
     * 根据父块ID查询所有子块
     */
    List<ParentChildChunk> findByParentIdOrderByChunkIndexAsc(String parentId);

    /**
     * 根据子块ID查询
     */
    Optional<ParentChildChunk> findByChildId(String childId);

    /**
     * 根据向量库ID查询
     */
    Optional<ParentChildChunk> findByChildVectorId(String childVectorId);

    /**
     * 根据文档ID查询所有块
     */
    List<ParentChildChunk> findByDocIdOrderByParentIdAscChunkIndexAsc(String docId);

    /**
     * 根据子块文本查询
     */
    List<ParentChildChunk> findByChildText(String childText);
}
