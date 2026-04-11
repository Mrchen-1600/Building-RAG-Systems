package com.example.demo02criticalissues.repository;

import com.example.demo02criticalissues.entity.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 文档元数据Repository
 */
@Repository
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, Long> {

    /**
     * 根据文档ID查询
     */
    Optional<DocumentMetadata> findByDocId(String docId);

    /**
     * 根据文档名称查询
     */
    Optional<DocumentMetadata> findByDocName(String docName);

    /**
     * 根据文档类型查询
     */
    java.util.List<DocumentMetadata> findByDocType(String docType);

    /**
     * 根据内容哈希查询（用于去重）
     */
    Optional<DocumentMetadata> findByContentHash(String contentHash);
}
