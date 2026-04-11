package com.example.demo02criticalissues.service;

import com.example.demo02criticalissues.context.ContextWindow;
import com.example.demo02criticalissues.entity.UserProfile;
import com.example.demo02criticalissues.repository.UserProfileRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 *  ClassName: UserProfileService
 *  Package: com.example.demo02criticalissues.service
 *
 *  @Author Mrchen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 加载用户的最新长期记忆
     *
     * @param userId 用户ID
     * @return 上下文窗口的长期记忆对象，如果用户不存在返回null
     */
    public ContextWindow.Memory.LongTermMemory loadLongTermMemory(String userId) {
        return userProfileRepository.findTopByUserIdOrderByVersionDesc(userId)
                .map(profile -> convertToLongTermMemory(profile))
                .orElse(null);
    }

    /**
     * 更新用户画像（创建新版本）
     *
     * @param userId          用户ID
     * @param currentProfile  当前上下文中的长期记忆
     * @param conversationSummary 对话摘要（用于分析用户偏好）
     */
    @Transactional
    public void updateUserProfile(String userId,
                                 ContextWindow.Memory.LongTermMemory currentProfile,
                                 String conversationSummary) {
        // 查询当前最大版本号
        Integer maxVersion = userProfileRepository.findMaxVersionByUserId(userId).orElse(0);

        // 标记旧版本为非激活
        userProfileRepository.findTopByUserIdOrderByVersionDesc(userId)
                .ifPresent(oldProfile -> {
                    oldProfile.setIsActive(false);
                    userProfileRepository.save(oldProfile);
                });

        // 创建新版本
        UserProfile newProfile = new UserProfile();
        newProfile.setUserId(userId);
        newProfile.setVersion(maxVersion + 1);
        newProfile.setIsActive(true);

        // 设置基本属性
        if (currentProfile != null) {
            newProfile.setUserRole(currentProfile.getUserRole());
            newProfile.setExpertiseLevel(currentProfile.getExpertiseLevel());
            newProfile.setLearningStyle(currentProfile.getLearningStyle());
            newProfile.setInteractionPattern(currentProfile.getInteractionPattern());
        }

        // 序列化列表属性
        try {
            if (currentProfile != null && currentProfile.getInterests() != null) {
                newProfile.setInterests(objectMapper.writeValueAsString(currentProfile.getInterests()));
            }

            // 可以根据对话摘要分析用户偏好并更新
            Map<String, Object> preferences = new HashMap<>();
            if (currentProfile != null && currentProfile.getUserRole() != null) {
                preferences.put("user_role", currentProfile.getUserRole());
            }
            preferences.put("last_conversation_time", System.currentTimeMillis());
            newProfile.setPreferences(objectMapper.writeValueAsString(preferences));

            // 分析对话摘要，提取常问话题
            if (conversationSummary != null && !conversationSummary.isEmpty()) {
                Map<String, Integer> topicFrequency = new HashMap<>();
                String[] keywords = {"RAG", "大模型", "AI", "技术", "文档", "查询", "检索", "数据库"};
                for (String keyword : keywords) {
                    if (conversationSummary.contains(keyword)) {
                        topicFrequency.put(keyword, topicFrequency.getOrDefault(keyword, 0) + 1);
                    }
                }
                if (!topicFrequency.isEmpty()) {
                    newProfile.setFrequentlyAskedTopics(objectMapper.writeValueAsString(topicFrequency));
                }
            }

        } catch (JsonProcessingException e) {
            log.error("JSON序列化失败", e);
        }

        userProfileRepository.save(newProfile);
        log.info("已更新用户 {} 的画像，版本号: {}", userId, maxVersion + 1);
    }

    /**
     * 判断是否需要更新用户画像
     *
     * @param userId             用户ID
     * @param currentProfile     当前上下文中的长期记忆
     * @param conversationSummary 对话摘要
     * @return true如果需要更新
     */
    public boolean shouldUpdateProfile(String userId,
                                      ContextWindow.Memory.LongTermMemory currentProfile,
                                      String conversationSummary) {
        // 获取最新版本
        Optional<UserProfile> latestProfile = userProfileRepository.findTopByUserIdOrderByVersionDesc(userId);

        if (latestProfile.isEmpty()) {
            return true; // 新用户需要创建
        }

        // 检查版本差异（版本号不一致时需要更新）
        if (currentProfile != null &&
            !Objects.equals(currentProfile.getVersion(), latestProfile.get().getVersion())) {
            return true;
        }

        // 检查对话摘要中是否有新的兴趣点
        if (conversationSummary != null && !conversationSummary.isEmpty()) {
            String[] keywords = {"新话题", "首次询问", "第一次"};
            for (String keyword : keywords) {
                if (conversationSummary.contains(keyword)) {
                    return true;
                }
            }
        }

        // 检查用户画像中是否有需要更新的字段
        if (currentProfile != null) {
            UserProfile profile = latestProfile.get();
            if (!Objects.equals(currentProfile.getUserRole(), profile.getUserRole()) ||
                !Objects.equals(currentProfile.getExpertiseLevel(), profile.getExpertiseLevel())) {
                return true;
            }
        }

        return false;
    }

    /**
     * 创建新用户画像
     *
     * @param userId 用户ID
     * @return 创建的用户画像
     */
    @Transactional
    public UserProfile createNewUserProfile(String userId) {
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setVersion(1);
        profile.setIsActive(true);
        profile.setUserRole("普通用户");
        profile.setExpertiseLevel("中级");
        profile.setLearningStyle("混合型");
        profile.setInteractionPattern("标准型");

        return userProfileRepository.save(profile);
    }

    /**
     * 获取用户的所有版本历史
     *
     * @param userId 用户ID
     * @return 用户画像历史列表
     */
    public List<UserProfile> getUserProfileHistory(String userId) {
        return userProfileRepository.findByUserIdOrderByVersionDesc(userId);
    }

    /**
     * 根据数据库记录转换为上下文窗口的长期记忆对象
     */
    private ContextWindow.Memory.LongTermMemory convertToLongTermMemory(UserProfile profile) {
        ContextWindow.Memory.LongTermMemory longTerm = new ContextWindow.Memory.LongTermMemory();
        longTerm.setUserId(profile.getUserId());
        longTerm.setVersion(profile.getVersion());
        longTerm.setUserRole(profile.getUserRole());
        longTerm.setExpertiseLevel(profile.getExpertiseLevel());
        longTerm.setLearningStyle(profile.getLearningStyle());
        longTerm.setInteractionPattern(profile.getInteractionPattern());

        // 反序列化兴趣列表
        if (profile.getInterests() != null) {
            try {
                longTerm.setInterests(objectMapper.readValue(profile.getInterests(), new TypeReference<List<String>>() {}));
            } catch (JsonProcessingException e) {
                log.error("JSON反序列化失败", e);
                longTerm.setInterests(List.of());
            }
        }

        return longTerm;
    }
}
