package com.example.demo02criticalissues.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ClassName: ContextWindow
 * Package: com.example.demo02criticalissues.context
 *
 * @Author Mrchen
 *
 * 上下文窗口结构化设计
 * 结构：
 * <system prompt> - 系统提示词
 * <core rules> - 核心规则
 * <tools or Skills> - 工具或skills描述
 * <memory>
 *   <short-term> - 短期记忆
 *   <long-term> - 长期记忆
 * </memory>
 * <input> - 用户当前提问内容
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextWindow {

    /**
     * 系统提示词
     */
    @Builder.Default
    private String systemPrompt = "你是一个智能助手，能够基于提供的知识库信息回答用户的问题。";

    /**
     * 核心规则
     */
    @Builder.Default
    private List<String> coreRules = List.of(
            "1. 只基于提供的【参考资料】回答问题",
            "2. 如果参考资料中没有相关信息，明确告知用户",
            "3. 保持回答的准确性和客观性",
            "4. 使用清晰易懂的语言组织答案"
    );

    /**
     * 工具或Skills描述
     */
    @Builder.Default
    private ToolsOrSkills toolsOrSkills = new ToolsOrSkills();

    /**
     * 记忆信息
     */
    @Builder.Default
    private Memory memory = new Memory();

    /**
     * 用户当前提问内容
     */
    private String input;

    /**
     * 检索到的上下文
     */
    @Builder.Default
    private String retrievedContext = "";

    /**
     * 工具或Skills描述
     */
    @Builder
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolsOrSkills {
        /**
         * 可用的工具列表
         */
        @Builder.Default
        private List<ToolDescription> tools = List.of();

        /**
         * 当前调用的Skill详细信息（如果有的话）
         */
        private String currentSkillDetail;

        /**
         * 工具描述
         */
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class ToolDescription {
            private String name;
            private String description;
            private String usage;
        }
    }

    /**
     * 记忆信息
     */
    @Builder
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Memory {
        /**
         * 短期记忆（最近几轮对话的摘要）
         */
        @Builder.Default
        private ShortTermMemory shortTerm = new ShortTermMemory();

        /**
         * 长期记忆（用户画像信息）
         */
        @Builder.Default
        private LongTermMemory longTerm = new LongTermMemory();

        /**
         * 短期记忆
         */
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class ShortTermMemory {
            /**
             * 对话轮次
             */
            @Builder.Default
            private int round = 0;

            /**
             * 历史对话摘要（超过3轮后压缩为摘要）
             */
            @Builder.Default
            private String summary = "";

            /**
             * 历史对话记录（最近3轮）
             */
            @Builder.Default
            private List<ConversationTurn> history = List.of();
        }

        /**
         * 长期记忆
         */
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class LongTermMemory {
            /**
             * 用户ID
             */
            private String userId;

            /**
             * 用户角色
             */
            @Builder.Default
            private String userRole = "普通用户";

            /**
             * 专业水平
             */
            @Builder.Default
            private String expertiseLevel = "中级";

            /**
             * 兴趣点
             */
            @Builder.Default
            private List<String> interests = List.of();

            /**
             * 学习风格
             */
            @Builder.Default
            private String learningStyle = "混合型";

            /**
             * 交互偏好
             */
            @Builder.Default
            private String interactionPattern = "标准型";

            /**
             * 版本号
             */
            @Builder.Default
            private int version = 1;
        }

        /**
         * 对话轮次记录
         */
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class ConversationTurn {
            /**
             * 轮次号
             */
            private int turn;

            /**
             * 用户提问
             */
            private String userQuery;

            /**
             * AI回复
             */
            private String aiResponse;

            /**
             * 获取轮次号
             */
            public int turn() {
                return getTurn();
            }

            /**
             * 获取用户提问
             */
            public String userQuery() {
                return getUserQuery();
            }

            /**
             * 获取AI回复
             */
            public String aiResponse() {
                return getAiResponse();
            }
        }
    }

    /**
     * 构建完整的提示词
     *
     * @return 完整的提示词
     */
    public String buildPrompt() {
        StringBuilder sb = new StringBuilder();

        // System Prompt
        sb.append("<system prompt>\n");
        sb.append(systemPrompt).append("\n");
        sb.append("</system prompt>\n\n");

        // Core Rules
        sb.append("<core rules>\n");
        for (String rule : coreRules) {
            sb.append(rule).append("\n");
        }
        sb.append("</core rules>\n\n");

        // Tools or Skills
        sb.append("<tools or Skills>\n");
        if (!toolsOrSkills.getTools().isEmpty()) {
            sb.append("可用工具:\n");
            for (ToolsOrSkills.ToolDescription tool : toolsOrSkills.getTools()) {
                sb.append("- ").append(tool.getName()).append(": ")
                        .append(tool.getDescription()).append("\n");
            }
        }
        if (toolsOrSkills.getCurrentSkillDetail() != null) {
            sb.append("\n当前使用的Skill详情:\n");
            sb.append(toolsOrSkills.getCurrentSkillDetail()).append("\n");
        }
        sb.append("</tools or Skills>\n\n");

        // Memory
        sb.append("<memory>\n");

        // Short-term memory
        sb.append("  <short-term>\n");
        if (memory.getShortTerm().getRound() == 0) {
            sb.append("    （暂无历史对话）\n");
        } else if (memory.getShortTerm().getSummary() != null && !memory.getShortTerm().getSummary().isEmpty()) {
            sb.append("    对话摘要: ").append(memory.getShortTerm().getSummary()).append("\n");
        } else {
            sb.append("    对话轮次: ").append(memory.getShortTerm().getRound()).append("\n");
            for (Memory.ConversationTurn turn : memory.getShortTerm().getHistory()) {
                sb.append("    第").append(turn.getTurn()).append("轮:\n");
                sb.append("      用户: ").append(turn.getUserQuery()).append("\n");
                sb.append("      AI: ").append(turn.getAiResponse()).append("\n");
            }
        }
        sb.append("  </short-term>\n");

        // Long-term memory
        sb.append("  <long-term>\n");
        if (memory.getLongTerm().getUserId() != null) {
            sb.append("    用户ID: ").append(memory.getLongTerm().getUserId()).append("\n");
            sb.append("    角色: ").append(memory.getLongTerm().getUserRole()).append("\n");
            sb.append("    专业水平: ").append(memory.getLongTerm().getExpertiseLevel()).append("\n");
            sb.append("    版本: ").append(memory.getLongTerm().getVersion()).append("\n");
            if (!memory.getLongTerm().getInterests().isEmpty()) {
                sb.append("    兴趣: ").append(String.join(", ", memory.getLongTerm().getInterests())).append("\n");
            }
        } else {
            sb.append("    （暂无长期记忆）\n");
        }
        sb.append("  </long-term>\n");

        sb.append("</memory>\n\n");

        // Input
        sb.append("<input>\n");
        sb.append(input).append("\n");
        sb.append("</input>\n");

        // Retrieved Context (if available)
        if (retrievedContext != null && !retrievedContext.isEmpty()) {
            sb.append("\n<retrieved context>\n");
            sb.append(retrievedContext).append("\n");
            sb.append("</retrieved context>\n");
        }

        return sb.toString();
    }

    /**
     * 更新短期记忆
     *
     * @param userQuery  用户提问
     * @param aiResponse AI回复
     */
    public void updateShortTermMemory(String userQuery, String aiResponse) {
        Memory.ShortTermMemory shortTerm = memory.getShortTerm();
        int newRound = shortTerm.getRound() + 1;

        Memory.ConversationTurn turn = new Memory.ConversationTurn(newRound, userQuery, aiResponse);
        List<Memory.ConversationTurn> newHistory = new java.util.ArrayList<>(shortTerm.getHistory());
        newHistory.add(turn);

        // 如果超过最大轮次（这里允许留存以便生成摘要时消费），不做强制切断
        shortTerm.setRound(newRound);
        shortTerm.setHistory(newHistory);
    }

    /**
     * 设置短期记忆摘要
     *
     * @param summary 摘要内容
     */
    public void setShortTermSummary(String summary) {
        Memory.ShortTermMemory shortTerm = memory.getShortTerm();
        shortTerm.setSummary(summary);
        // 设置摘要后清空历史记录
        shortTerm.setHistory(List.of());
    }

    /**
     * 检查是否需要触发摘要
     *
     * @return true 如果轮次大于3
     */
    public boolean shouldTriggerSummary() {
        Memory.ShortTermMemory shortTerm = memory.getShortTerm();
        return shortTerm.getHistory().size() >= 3;
    }

    /**
     * 清空短期记忆
     */
    public void clearShortTermMemory() {
        memory.setShortTerm(new Memory.ShortTermMemory());
    }
}
