package com.example.demo02criticalissues.context;

import com.example.demo02criticalissues.context.ContextWindow.Memory.ConversationTurn;
import com.example.demo02criticalissues.context.ContextWindow.ToolsOrSkills.ToolDescription;

import java.util.ArrayList;
import java.util.List;

/**
 *  ClassName: ContextWindowBuilder
 *  Package: com.example.demo02criticalissues.context
 *
 *  @Author Mrchen
 *
 * 上下文窗口构建器
 * 提供流式API来构建结构化的上下文窗口
 */
public class ContextWindowBuilder {

    private final ContextWindow contextWindow;

    public ContextWindowBuilder() {
        this.contextWindow = new ContextWindow();
    }

    public ContextWindowBuilder(String userInput) {
        this.contextWindow = ContextWindow.builder().input(userInput).build();
    }

    /**
     * 设置系统提示词
     */
    public ContextWindowBuilder systemPrompt(String systemPrompt) {
        contextWindow.setSystemPrompt(systemPrompt);
        return this;
    }

    /**
     * 设置用户输入
     */
    public ContextWindowBuilder input(String input) {
        contextWindow.setInput(input);
        return this;
    }

    /**
     * 设置核心规则
     */
    public ContextWindowBuilder coreRules(List<String> coreRules) {
        contextWindow.setCoreRules(coreRules);
        return this;
    }

    /**
     * 添加核心规则
     */
    public ContextWindowBuilder addCoreRule(String rule) {
        List<String> rules = new ArrayList<>(contextWindow.getCoreRules());
        rules.add(rule);
        contextWindow.setCoreRules(rules);
        return this;
    }

    /**
     * 设置工具列表
     */
    public ContextWindowBuilder tools(List<ToolDescription> tools) {
        ContextWindow.ToolsOrSkills toolsOrSkills = new ContextWindow.ToolsOrSkills();
        toolsOrSkills.setTools(tools);
        contextWindow.setToolsOrSkills(toolsOrSkills);
        return this;
    }

    /**
     * 添加工具
     */
    public ContextWindowBuilder addTool(String name, String description, String usage) {
        ContextWindow.ToolsOrSkills toolsOrSkills = contextWindow.getToolsOrSkills();
        if (toolsOrSkills == null) {
            toolsOrSkills = new ContextWindow.ToolsOrSkills();
            contextWindow.setToolsOrSkills(toolsOrSkills);
        }
        List<ToolDescription> tools = new ArrayList<>(toolsOrSkills.getTools());
        tools.add(new ToolDescription(name, description, usage));
        toolsOrSkills.setTools(tools);
        return this;
    }

    /**
     * 设置当前Skill详情
     */
    public ContextWindowBuilder currentSkillDetail(String skillDetail) {
        ContextWindow.ToolsOrSkills toolsOrSkills = contextWindow.getToolsOrSkills();
        if (toolsOrSkills == null) {
            toolsOrSkills = new ContextWindow.ToolsOrSkills();
        }
        toolsOrSkills.setCurrentSkillDetail(skillDetail);
        contextWindow.setToolsOrSkills(toolsOrSkills);
        return this;
    }

    /**
     * 设置用户ID
     */
    public ContextWindowBuilder userId(String userId) {
        ContextWindow.Memory memory = contextWindow.getMemory();
        if (memory == null) {
            memory = new ContextWindow.Memory();
        }
        ContextWindow.Memory.LongTermMemory longTerm = memory.getLongTerm();
        if (longTerm == null) {
            longTerm = new ContextWindow.Memory.LongTermMemory();
        }
        longTerm.setUserId(userId);
        memory.setLongTerm(longTerm);
        contextWindow.setMemory(memory);
        return this;
    }

    /**
     * 设置用户角色
     */
    public ContextWindowBuilder userRole(String userRole) {
        ensureLongTermMemory().setUserRole(userRole);
        return this;
    }

    /**
     * 设置专业水平
     */
    public ContextWindowBuilder expertiseLevel(String expertiseLevel) {
        ensureLongTermMemory().setExpertiseLevel(expertiseLevel);
        return this;
    }

    /**
     * 设置兴趣点
     */
    public ContextWindowBuilder interests(List<String> interests) {
        ensureLongTermMemory().setInterests(interests);
        return this;
    }

    /**
     * 设置学习风格
     */
    public ContextWindowBuilder learningStyle(String learningStyle) {
        ensureLongTermMemory().setLearningStyle(learningStyle);
        return this;
    }

    /**
     * 设置交互偏好
     */
    public ContextWindowBuilder interactionPattern(String interactionPattern) {
        ensureLongTermMemory().setInteractionPattern(interactionPattern);
        return this;
    }

    /**
     * 设置对话轮次
     */
    public ContextWindowBuilder conversationRound(int round) {
        ensureShortTermMemory().setRound(round);
        return this;
    }

    /**
     * 设置对话摘要
     */
    public ContextWindowBuilder conversationSummary(String summary) {
        ensureShortTermMemory().setSummary(summary);
        return this;
    }

    /**
     * 添加对话历史
     */
    public ContextWindowBuilder addConversationHistory(List<ConversationTurn> history) {
        ensureShortTermMemory().setHistory(history);
        return this;
    }

    /**
     * 添加单轮对话历史
     */
    public ContextWindowBuilder addConversationTurn(int turn, String userQuery, String aiResponse) {
        ContextWindow.Memory memory = contextWindow.getMemory();
        if (memory == null) {
            memory = new ContextWindow.Memory();
        }
        ContextWindow.Memory.ShortTermMemory shortTerm = memory.getShortTerm();
        if (shortTerm == null) {
            shortTerm = new ContextWindow.Memory.ShortTermMemory();
        }
        List<ConversationTurn> history = new ArrayList<>(shortTerm.getHistory());
        history.add(new ConversationTurn(turn, userQuery, aiResponse));
        shortTerm.setHistory(history);
        memory.setShortTerm(shortTerm);
        contextWindow.setMemory(memory);
        return this;
    }

    /**
     * 设置检索到的上下文
     */
    public ContextWindowBuilder retrievedContext(String retrievedContext) {
        contextWindow.setRetrievedContext(retrievedContext);
        return this;
    }

    /**
     * 确保长期记忆对象存在
     */
    private ContextWindow.Memory.LongTermMemory ensureLongTermMemory() {
        ContextWindow.Memory memory = contextWindow.getMemory();
        if (memory == null) {
            memory = new ContextWindow.Memory();
            contextWindow.setMemory(memory);
        }
        ContextWindow.Memory.LongTermMemory longTerm = memory.getLongTerm();
        if (longTerm == null) {
            longTerm = new ContextWindow.Memory.LongTermMemory();
            memory.setLongTerm(longTerm);
        }
        return longTerm;
    }

    /**
     * 确保短期记忆对象存在
     */
    private ContextWindow.Memory.ShortTermMemory ensureShortTermMemory() {
        ContextWindow.Memory memory = contextWindow.getMemory();
        if (memory == null) {
            memory = new ContextWindow.Memory();
            contextWindow.setMemory(memory);
        }
        ContextWindow.Memory.ShortTermMemory shortTerm = memory.getShortTerm();
        if (shortTerm == null) {
            shortTerm = new ContextWindow.Memory.ShortTermMemory();
            memory.setShortTerm(shortTerm);
        }
        return shortTerm;
    }

    /**
     * 构建上下文窗口
     */
    public ContextWindow build() {
        return contextWindow;
    }

    /**
     * 构建并返回提示词字符串
     */
    public String buildPrompt() {
        return contextWindow.buildPrompt();
    }

    /**
     * 从已有的上下文窗口创建构建器
     */
    public static ContextWindowBuilder from(ContextWindow contextWindow) {
        ContextWindowBuilder builder = new ContextWindowBuilder();
        builder.contextWindow.setSystemPrompt(contextWindow.getSystemPrompt());
        builder.contextWindow.setCoreRules(contextWindow.getCoreRules());
        builder.contextWindow.setToolsOrSkills(contextWindow.getToolsOrSkills());
        builder.contextWindow.setMemory(contextWindow.getMemory());
        builder.contextWindow.setInput(contextWindow.getInput());
        builder.contextWindow.setRetrievedContext(contextWindow.getRetrievedContext());
        return builder;
    }
}
