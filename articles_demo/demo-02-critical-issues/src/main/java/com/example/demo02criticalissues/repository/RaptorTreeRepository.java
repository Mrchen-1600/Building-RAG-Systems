package com.example.demo02criticalissues.repository;

import com.example.demo02criticalissues.entity.RaptorTree;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * RAPTOR树状摘要Repository
 */
@Repository
public interface RaptorTreeRepository extends JpaRepository<RaptorTree, Long> {

    /**
     * 根据节点ID查询
     */
    Optional<RaptorTree> findByNodeId(String nodeId);

    /**
     * 获取第一个指定类型的节点
     */
    Optional<RaptorTree> findFirstByNodeType(String nodeType);

    /**
     * 根据文档ID查询所有节点
     */
    List<RaptorTree> findByDocIdOrderByLevelAscNodeId(String docId);

    /**
     * 根据文档ID和层级查询
     */
    List<RaptorTree> findByDocIdAndLevelOrderByNodeId(String docId, Integer level);

    /**
     * 根据父节点ID查询子节点
     */
    List<RaptorTree> findByParentNodeIdOrderByNodeId(String parentNodeId);

    /**
     * 根据聚类ID查询节点
     */
    List<RaptorTree> findByClusterIdOrderByNodeId(String clusterId);

    /**
     * 根据文档ID和层级、聚类ID查询
     */
    @Query("SELECT rt FROM RaptorTree rt WHERE rt.docId = :docId AND rt.level = :level AND rt.clusterId = :clusterId " +
            "ORDER BY rt.nodeId")
    List<RaptorTree> findByDocIdAndLevelAndClusterId(@Param("docId") String docId,
                                                     @Param("level") Integer level,
                                                     @Param("clusterId") String clusterId);

    /**
     * 查询文档的最大层级
     */
    @Query("SELECT MAX(rt.level) FROM RaptorTree rt WHERE rt.docId = :docId")
    Optional<Integer> findMaxLevelByDocId(@Param("docId") String docId);

    /**
     * 查询文档的根节点
     */
    @Query("SELECT rt FROM RaptorTree rt WHERE rt.docId = :docId AND rt.nodeType = 'root'")
    Optional<RaptorTree> findRootNodeByDocId(@Param("docId") String docId);
}