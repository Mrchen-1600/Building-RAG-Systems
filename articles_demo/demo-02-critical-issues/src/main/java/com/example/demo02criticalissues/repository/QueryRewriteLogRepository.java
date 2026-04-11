package com.example.demo02criticalissues.repository;

import com.example.demo02criticalissues.entity.QueryRewriteLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 查询改写记录Repository
 */
@Repository
public interface QueryRewriteLogRepository extends JpaRepository<QueryRewriteLog, Long> {

    /**
     * 根据用户ID查询改写记录
     */
    List<QueryRewriteLog> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * 根据用户ID和改写类型查询
     */
    List<QueryRewriteLog> findByUserIdAndRewriteTypeOrderByCreatedAtDesc(String userId, String rewriteType);

    /**
     * 根据时间范围查询
     */
    List<QueryRewriteLog> findByUserIdAndCreatedAtBetween(String userId, LocalDateTime start, LocalDateTime end);
}
