package com.example.demo02criticalissues.repository;

import com.example.demo02criticalissues.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户画像Repository
 */
@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    /**
     * 根据用户ID和版本查询
     */
    Optional<UserProfile> findByUserIdAndVersion(String userId, Integer version);

    /**
     * 根据用户ID查询最新版本
     */
    Optional<UserProfile> findTopByUserIdOrderByVersionDesc(String userId);

    /**
     * 查询用户的所有版本
     */
    java.util.List<UserProfile> findByUserIdOrderByVersionDesc(String userId);

    /**
     * 根据用户ID查询当前激活的版本
     */
    Optional<UserProfile> findByUserIdAndIsActiveTrue(String userId);

    /**
     * 获取用户的最大版本号
     */
    @Query("SELECT MAX(u.version) FROM UserProfile u WHERE u.userId = :userId")
    Optional<Integer> findMaxVersionByUserId(@Param("userId") String userId);
}
