package com.example.demo02criticalissues.skill;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *  ClassName: SkillManager
 *  Package: com.example.demo02criticalissues.skill
 *
 *  @Author Mrchen
 *
 * Skill 管理系统
 * 实现 Skill 的渐进式加载：
 * 1. 首先加载 yml 配置（name 和 description）用于快速识别
 * 2. 确认使用该 skill 后，再加载完整的 skills.md 文档
 * 3. 如有需要，额外加载 ref 和 script 目录下的文件
 */
@Slf4j
@Component
public class SkillManager {

    @Value("${skill.base-path:classpath:skills}")
    private String skillBasePath;

    @Value("${skill.lazy-load-enabled:true}")
    private boolean lazyLoadEnabled;

    /**
     * Skill 基础信息
     */
    @Data
    public static class SkillMetadata {
        private String name;
        private String description;
        private String version;
        private String author;
        private List<String> keywords;
        private List<String> intentTypes;
        private boolean lazyLoadFullDoc;
        private boolean enableRefs;
        private boolean enableScripts;
    }

    /**
     * Skill 完整信息（包含详细文档）
     */
    @Data
    public static class SkillInfo {
        private SkillMetadata metadata;
        private String fullDocument;       // 完整的 skills.md 内容
        private Map<String, String> refs;  // 参考文档 <filename, content>
        private Map<String, String> scripts; // 脚本文件 <filename, content>
    }

    /**
     * Skill 配置（从 yml 解析）
     */
    @Data
    public static class SkillConfig {
        private String name;
        private String description;
        private String version;
        private String author;
        private TriggerConfig trigger;
        private LoadingConfig loading;
        private DatabaseConfig database;
        private SecurityConfig security;
        private PerformanceConfig performance;

        @Data
        public static class TriggerConfig {
            private List<String> keywords;
            private List<String> intentTypes;
        }

        @Data
        public static class LoadingConfig {
            private boolean lazyLoadFullDoc;
            private boolean enableRefs;
            private boolean enableScripts;
        }

        @Data
        public static class DatabaseConfig {
            private String schema;
            private List<String> tables;
        }

        @Data
        public static class SecurityConfig {
            private List<String> allowedOperations;
            private boolean enableInjectionCheck;
            private int maxLimit;
        }

        @Data
        public static class PerformanceConfig {
            private int timeoutMs;
            private boolean enableCache;
        }
    }

    // 缓存已加载的 Skill 元信息
    private final Map<String, SkillMetadata> metadataCache = new ConcurrentHashMap<>();

    // 缓存已加载的完整 Skill 信息
    private final Map<String, SkillInfo> fullInfoCache = new ConcurrentHashMap<>();

    /**
     * 获取所有可用的 Skill 列表（仅加载 metadata，轻量级）
     *
     * @return Skill 名称到元信息的映射
     */
    public Map<String, SkillMetadata> getAllSkillMetadata() {
        Map<String, SkillMetadata> result = new HashMap<>();

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] skillDirs = resolver.getResources("classpath:skills/*/");

            for (Resource dir : skillDirs) {
                String skillName = dir.getFilename();
                if (skillName != null) {
                    SkillMetadata metadata = getSkillMetadata(skillName);
                    if (metadata != null) {
                        result.put(skillName, metadata);
                    }
                }
            }
        } catch (IOException e) {
            log.error("获取 Skill 列表失败", e);
        }

        return result;
    }

    /**
     * 获取 Skill 元信息（仅加载 yml，轻量级）
     *
     * @param skillName Skill 名称
     * @return Skill 元信息
     */
    public SkillMetadata getSkillMetadata(String skillName) {
        // 先检查缓存
        if (metadataCache.containsKey(skillName)) {
            return metadataCache.get(skillName);
        }

        try {
            String ymlPath = "classpath:skills/" + skillName + "/skill.yml";
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource ymlResource = resolver.getResource(ymlPath);

            if (!ymlResource.exists()) {
                log.warn("Skill {} 的 skill.yml 不存在", skillName);
                return null;
            }

            try (InputStream is = ymlResource.getInputStream()) {
                Yaml yaml = new Yaml();
                SkillConfig config = yaml.loadAs(is, SkillConfig.class);

                SkillMetadata metadata = convertToMetadata(config);
                metadataCache.put(skillName, metadata);
                return metadata;
            }
        } catch (IOException e) {
            log.error("加载 Skill {} 元信息失败", skillName, e);
            return null;
        }
    }

    /**
     * 获取 Skill 完整信息（渐进式加载：先 yml，确认使用后加载完整文档）
     *
     * @param skillName Skill 名称
     * @param forceReload 是否强制重新加载
     * @return Skill 完整信息
     */
    public SkillInfo getSkillInfo(String skillName, boolean forceReload) {
        if (!forceReload && fullInfoCache.containsKey(skillName)) {
            return fullInfoCache.get(skillName);
        }

        // 先确保元信息已加载
        SkillMetadata metadata = getSkillMetadata(skillName);
        if (metadata == null) {
            log.warn("Skill {} 的元信息加载失败，无法获取完整信息", skillName);
            return null;
        }

        // 如果启用懒加载，且还没加载过完整文档
        if (lazyLoadEnabled && metadata.isLazyLoadFullDoc() && !fullInfoCache.containsKey(skillName)) {
            log.info("Skill {} 启用懒加载，仅加载元信息", skillName);
            SkillInfo info = new SkillInfo();
            info.setMetadata(metadata);
            // 不加载完整文档
            return info;
        }

        try {
            SkillInfo info = loadFullSkillInfo(skillName, metadata);
            fullInfoCache.put(skillName, info);
            return info;
        } catch (IOException e) {
            log.error("加载 Skill {} 完整信息失败", skillName, e);
            return null;
        }
    }

    /**
     * 获取 Skill 完整信息（强制加载）
     *
     * @param skillName Skill 名称
     * @return Skill 完整信息
     */
    public SkillInfo getSkillInfo(String skillName) {
        return getSkillInfo(skillName, false);
    }


    /**
     * 加载 Skill 完整信息
     */
    private SkillInfo loadFullSkillInfo(String skillName, SkillMetadata metadata) throws IOException {
        SkillInfo info = new SkillInfo();
        info.setMetadata(metadata);
        info.setRefs(new HashMap<>());
        info.setScripts(new HashMap<>());

        String basePath = "classpath:skills/" + skillName + "/";

        // 1. 加载完整的 skills.md
        try {
            String skillsMd = loadResourceContent(basePath + "skills.md");
            info.setFullDocument(skillsMd);
        } catch (IOException e) {
            log.warn("Skill {} 不存在 skills.md 或加载失败", skillName);
            info.setFullDocument("");
        }

        // 2. 如果启用，加载 ref 目录下的文件
        if (metadata.isEnableRefs()) {
            loadDirectoryFiles(basePath + "ref/", info.getRefs());
        }

        // 3. 如果启用，加载 script 目录下的文件
        if (metadata.isEnableScripts()) {
            loadDirectoryFiles(basePath + "script/", info.getScripts());
        }

        return info;
    }

    /**
     * 加载目录下所有文件
     */
    private void loadDirectoryFiles(String basePath, Map<String, String> targetMap) {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] files = resolver.getResources(basePath + "*");

            for (Resource file : files) {
                String filename = file.getFilename();
                if (filename != null) {
                    try (InputStream is = file.getInputStream()) {
                        String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        targetMap.put(filename, content);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("加载目录 {} 失败或目录不存在", basePath);
        }
    }

    /**
     * 加载资源文件内容
     */
    private String loadResourceContent(String resourcePath) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource resource = resolver.getResource(resourcePath);

        if (!resource.exists()) {
            throw new IOException("资源不存在: " + resourcePath);
        }

        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 检查是否应该触发某个 Skill
     *
     * @param skillName Skill 名称
     * @param query     用户查询
     * @param intentType 意图类型
     * @return true 如果应该使用该 Skill
     */
    public boolean shouldUseSkill(String skillName, String query, String intentType) {
        SkillMetadata metadata = getSkillMetadata(skillName);
        if (metadata == null) {
            return false;
        }

        // 检查意图类型是否匹配
        if (metadata.getIntentTypes() != null && metadata.getIntentTypes().contains(intentType)) {
            return true;
        }

        // 检查关键词是否匹配
        if (metadata.getKeywords() != null) {
            String lowerQuery = query.toLowerCase();
            for (String keyword : metadata.getKeywords()) {
                if (lowerQuery.contains(keyword.toLowerCase())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 将 SkillConfig 转换为 SkillMetadata
     */
    private SkillMetadata convertToMetadata(SkillConfig config) {
        SkillMetadata metadata = new SkillMetadata();
        metadata.setName(config.getName());
        metadata.setDescription(config.getDescription());
        metadata.setVersion(config.getVersion());
        metadata.setAuthor(config.getAuthor());

        if (config.getTrigger() != null) {
            metadata.setKeywords(config.getTrigger().getKeywords());
            metadata.setIntentTypes(config.getTrigger().getIntentTypes());
        }

        if (config.getLoading() != null) {
            metadata.setLazyLoadFullDoc(config.getLoading().isLazyLoadFullDoc());
            metadata.setEnableRefs(config.getLoading().isEnableRefs());
            metadata.setEnableScripts(config.getLoading().isEnableScripts());
        }

        return metadata;
    }

    /**
     * 获取 Skill 的数据库表信息
     *
     * @param skillName Skill 名称
     * @return 表名列表
     */
    public List<String> getSkillTables(String skillName) {
        try {
            String ymlPath = "classpath:skills/" + skillName + "/skill.yml";
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource ymlResource = resolver.getResource(ymlPath);

            if (!ymlResource.exists()) {
                return List.of();
            }

            try (InputStream is = ymlResource.getInputStream()) {
                Yaml yaml = new Yaml();
                SkillConfig config = yaml.loadAs(is, SkillConfig.class);

                if (config.getDatabase() != null) {
                    return config.getDatabase().getTables();
                }
            }
        } catch (IOException e) {
            log.error("获取 Skill {} 的数据库表信息失败", skillName, e);
        }

        return List.of();
    }

    /**
     * 获取 Skill 的安全配置
     *
     * @param skillName Skill 名称
     * @return 安全配置
     */
    public SkillConfig.SecurityConfig getSecurityConfig(String skillName) {
        try {
            String ymlPath = "classpath:skills/" + skillName + "/skill.yml";
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource ymlResource = resolver.getResource(ymlPath);

            if (!ymlResource.exists()) {
                return null;
            }

            try (InputStream is = ymlResource.getInputStream()) {
                Yaml yaml = new Yaml();
                SkillConfig config = yaml.loadAs(is, SkillConfig.class);
                return config.getSecurity();
            }
        } catch (IOException e) {
            log.error("获取 Skill {} 的安全配置失败", skillName, e);
        }

        return null;
    }

    /**
     * 清除缓存（用于刷新 Skill 配置）
     *
     * @param skillName Skill 名称，null 表示清除所有
     */
    public void clearCache(String skillName) {
        if (skillName == null) {
            metadataCache.clear();
            fullInfoCache.clear();
            log.info("已清除所有 Skill 缓存");
        } else {
            metadataCache.remove(skillName);
            fullInfoCache.remove(skillName);
            log.info("已清除 Skill {} 的缓存", skillName);
        }
    }
}
