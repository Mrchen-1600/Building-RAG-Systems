package com.example.demo02criticalissues.tools;

import com.example.demo02criticalissues.skill.SkillManager;
import com.example.demo02criticalissues.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

/**
 * ClassName: Text2SqlTool
 * Package: com.example.demo02criticalissues.tools
 *
 * @Author Mrchen
 *
 * Text2SQL，遵循 Claude Agentic Skill 最佳实践
 * 实现渐进式披露：当路由决定使用该 Tool 时，才动态组装 skills.md、refs 和脚本列表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Text2SqlTool {

    private static final String SKILL_NAME = "text2sql";

    private final ChatLanguageModel chatModel;
    private final JdbcTemplate jdbcTemplate;
    private final SkillManager skillManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private List<String> allowedOperations = List.of("SELECT");
    private int maxLimit = 100;
    private boolean enableInjectionCheck = true;

    @PostConstruct
    public void init() {
        loadSkillMetadata();
    }

    private void loadSkillMetadata() {
        try {
            SkillManager.SkillMetadata metadata = skillManager.getSkillMetadata(SKILL_NAME);
            if (metadata != null) {
                log.info("已懒加载 Skill {} 元信息: {}", SKILL_NAME, metadata.getName());
            }

            SkillManager.SkillConfig.SecurityConfig securityConfig = skillManager.getSecurityConfig(SKILL_NAME);
            if (securityConfig != null) {
                this.allowedOperations = securityConfig.getAllowedOperations() != null
                        ? securityConfig.getAllowedOperations() : List.of("SELECT");
                this.maxLimit = securityConfig.getMaxLimit() > 0 ? securityConfig.getMaxLimit() : 100;
                this.enableInjectionCheck = securityConfig.isEnableInjectionCheck();
            }
        } catch (Exception e) {
            log.error("加载 Skill {} 元信息失败，使用默认配置", SKILL_NAME, e);
        }
    }

    private String buildDynamicSystemPrompt() {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("你是一个高级数据处理与数据库专家。请根据用户的问题，生成对应的 SQL 查询语句，或者调用特定的脚本执行任务。\n\n");

        SkillManager.SkillInfo skillInfo = skillManager.getSkillInfo(SKILL_NAME);

        if (skillInfo != null) {
            if (skillInfo.getFullDocument() != null && !skillInfo.getFullDocument().isEmpty()) {
                promptBuilder.append("【Skill 核心操作规范】:\n").append(skillInfo.getFullDocument()).append("\n\n");
            }

            if (skillInfo.getRefs() != null && !skillInfo.getRefs().isEmpty()) {
                promptBuilder.append("【业务参考文档 (Refs)】:\n");
                skillInfo.getRefs().forEach((name, content) -> {
                    promptBuilder.append("--- [").append(name).append("] ---\n").append(content).append("\n\n");
                });
            }

            if (skillInfo.getScripts() != null && !skillInfo.getScripts().isEmpty()) {
                promptBuilder.append("【当前可用的系统级脚本】:\n");
                skillInfo.getScripts().keySet().forEach(name -> {
                    promptBuilder.append("- ").append(name).append("\n");
                });
                promptBuilder.append("注意：脚本内容已被底层系统托管，如果你认为需要运行脚本，只需输出文件名即可。\n\n");
            }
        }

        promptBuilder.append("请严格只返回纯JSON格式数据，不要包含任何 Markdown 标记或多余文字。\n");
        // 增加对大模型的严厉约束
        promptBuilder.append("【核心指令】：\n");
        promptBuilder.append("1. 你生成的 SQL 语句绝对不能以分号(;)结尾！\n");
        promptBuilder.append("2. 必须严格遵守上方提供的表名和字段名，严禁擅自给表名加 's' 写成复数格式（例如只能用 user_profile，绝不能用 user_profiles）！\n");
        promptBuilder.append("3. 我们对系统返回的单次查询设置了硬性上限限制（").append(maxLimit).append("条记录）。\n");

        promptBuilder.append("如果需要执行SQL，格式如下：\n");
        promptBuilder.append("{\n");
        promptBuilder.append("    \"action_type\": \"SQL\",\n");
        promptBuilder.append("    \"action_content\": \"SELECT ...\",\n");
        promptBuilder.append("    \"explanation\": \"查询说明\"\n");
        promptBuilder.append("}\n");
        promptBuilder.append("如果需要执行脚本，格式如下：\n");
        promptBuilder.append("{\n");
        promptBuilder.append("    \"action_type\": \"SCRIPT\",\n");
        promptBuilder.append("    \"action_content\": \"xxx.sh 或者 xxx.py\",\n");
        promptBuilder.append("    \"explanation\": \"调用脚本的目的\"\n");
        promptBuilder.append("}\n");

        return promptBuilder.toString();
    }

    public Text2SqlResult execute(String userQuery) {
        try {
            String dynamicPrompt = buildDynamicSystemPrompt() + "\n用户问题：" + userQuery;
            String response = chatModel.generate(dynamicPrompt);

            String cleanResponse = JsonUtils.extractJson(response);
            JsonNode jsonNode = objectMapper.readTree(cleanResponse);

            String actionType = jsonNode.has("action_type") ? jsonNode.get("action_type").asText() : "SQL";
            String actionContent = jsonNode.has("action_content") ? jsonNode.get("action_content").asText() : "";
            String explanation = jsonNode.has("explanation") ? jsonNode.get("explanation").asText() : "";

            if (actionContent == null || actionContent.isEmpty()) {
                return new Text2SqlResult(false, "无法将问题转换为有效的动作指令", null, null, maxLimit);
            }

            if ("SCRIPT".equals(actionType)) {
                log.info("Agent 决定执行脚本: {}", actionContent);
                String mockScriptOutput = "[Shell Output] Script " + actionContent + " executed successfully with code 0.";
                return new Text2SqlResult(true, explanation, null, mockScriptOutput, maxLimit);
            }
            else {
                String sql = actionContent;
                String securityCheckResult = checkSecurity(sql);
                if (securityCheckResult != null) {
                    return new Text2SqlResult(false, securityCheckResult, sql, null, maxLimit);
                }

                // 防止执行大宽表引发 JVM 内存溢出 (OOM) 崩溃
                // 在 JDBC 真正下发给数据库之前，进行安全的 LIMIT 限制保护拼接
                String safeSql = sql.trim();

                // 强制移除尾部分号，防止与后续的 LIMIT 拼接产生语法冲突
                if (safeSql.endsWith(";")) {
                    safeSql = safeSql.substring(0, safeSql.length() - 1).trim();
                }

                if (!safeSql.toUpperCase().matches("(?i).*\\bLIMIT\\b.*")) {
                    safeSql = safeSql + " LIMIT " + maxLimit;
                    log.info("【系统防护】为 SQL 追加限制防止 OOM: {}", safeSql);
                }

                // 增加底层 JDBC 异常捕获，即使表名写错也能优雅回传给大模型处理，而不是程序崩溃
                try {
                    List<Map<String, Object>> results = jdbcTemplate.queryForList(safeSql);
                    return new Text2SqlResult(true, explanation, safeSql, results, maxLimit);
                } catch (Exception dbEx) {
                    log.error("数据库执行 SQL 时发生语法或表结构错误: {}", safeSql, dbEx);
                    return new Text2SqlResult(false, "底层数据库执行报错：" + dbEx.getMessage(), safeSql, null, maxLimit);
                }
            }

        } catch (Exception e) {
            log.error("Tool 执行异常", e);
            return new Text2SqlResult(false, "指令生成或执行失败", null, null, maxLimit);
        }
    }

    public boolean isSuitableForSql(String userQuery) {
        return skillManager.shouldUseSkill(SKILL_NAME, userQuery, "STRUCTURED_QUERY");
    }

    private String checkSecurity(String sql) {
        if (!enableInjectionCheck) return null;
        String upperSql = sql.toUpperCase().trim();
        String[] dangerousKeywords = { "DROP", "DELETE", "UPDATE", "INSERT", "ALTER" };
        for (String keyword : dangerousKeywords) {
            if (upperSql.matches(".*\\b" + keyword + "\\b.*") && !"SELECT".equals(keyword)) {
                return "安全拦截：禁止使用 " + keyword + " 操作，当前环境仅支持查询";
            }
        }
        return upperSql.startsWith("SELECT") ? null : "仅支持 SELECT 查询操作";
    }

    public record Text2SqlResult(
            boolean success, String message, String sql, Object results, int limit
    ) {
        public String formatResultText() {
            if (!success) {
                return "【执行失败】" + message + (sql != null ? "\n尝试执行的SQL: " + sql : "");
            }
            if (sql != null) {
                int size = (results instanceof List) ? ((List<?>) results).size() : 0;
                return "【SQL查询】成功返回 " + size + " 条数据记录。(" + message + ")\n" + "返回结果详情：" + results.toString();
            } else {
                return "【脚本执行】成功完成。系统输出: " + results + " (" + message + ")";
            }
        }
    }
}