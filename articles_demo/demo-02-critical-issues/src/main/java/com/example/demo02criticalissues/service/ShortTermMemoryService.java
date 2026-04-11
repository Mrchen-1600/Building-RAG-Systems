package com.example.demo02criticalissues.service;

import com.example.demo02criticalissues.context.ContextWindow;
import com.example.demo02criticalissues.repository.ConversationArchiveRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * ClassName: ShortTermMemoryService
 * Package: com.example.demo02criticalissues.service
 *
 * @Author Mrchen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortTermMemoryService {

    private final ChatLanguageModel chatModel;
    private final ConversationArchiveRepository conversationArchiveRepository;
    private final UserProfileService userProfileService;

    @Value("${memory.short-term.max-rounds:3}")
    private int maxRounds;

    @Value("${memory.long-term.update-interval:5}")
    private int longTermUpdateInterval;

    /**
     * 更新短期记忆
     *
     * @param contextWindow 上下文窗口
     * @param userQuery     用户提问
     * @param aiResponse    AI回复
     */
    public void updateShortTermMemory(ContextWindow contextWindow, String userQuery, String aiResponse) {
        // 先添加新的对话轮次
        contextWindow.updateShortTermMemory(userQuery, aiResponse);
        log.info("已更新短期记忆缓存，当前待压缩队列条数: {}", contextWindow.getMemory().getShortTerm().getHistory().size());

        // 检查是否达到需要压缩摘要的阈值
        if (contextWindow.shouldTriggerSummary()) {
            triggerSummaryAndLongTermUpdate(contextWindow);
        }
    }

    /**
     * 触发摘要和长期记忆更新
     *
     * @param contextWindow 上下文窗口
     */
    private void triggerSummaryAndLongTermUpdate(ContextWindow contextWindow) {
        ContextWindow.Memory.ShortTermMemory shortTerm = contextWindow.getMemory().getShortTerm();

        // 获取旧的摘要（如果有）
        String oldSummary = shortTerm.getSummary();

        log.info("记忆数组已达阈值，正在调用 LLM 进行后台无损滚动摘要压缩...");
        // 生成融合了新旧记忆的滚动摘要
        String newSummary = generateRollingConversationSummary(oldSummary, shortTerm.getHistory());

        // 更新长期记忆（如果需要）
        String userId = contextWindow.getMemory().getLongTerm().getUserId();
        if (userId != null && userProfileService.shouldUpdateProfile(
                userId, contextWindow.getMemory().getLongTerm(), newSummary)) {
            userProfileService.updateUserProfile(userId,
                    contextWindow.getMemory().getLongTerm(), newSummary);
            log.info("已通过对话摘要提取画像特征，触发长期记忆更新 - 用户: {}", userId);
        }

        // 设置新摘要，清空已被压缩的历史记录
        contextWindow.setShortTermSummary(newSummary);
        log.info("滚动摘要生成完成，原占用的大量 Token 的历史 QA 数组已被清空。");
    }

    /**
     * 生成滚动对话摘要（Rolling Summary）
     * 将旧的摘要与新的对话记录融合，确保记忆的连续性
     */
    private String generateRollingConversationSummary(String oldSummary, List<ContextWindow.Memory.ConversationTurn> history) {
        if (history == null || history.isEmpty()) {
            return oldSummary != null ? oldSummary : "";
        }

        StringBuilder historyBuilder = new StringBuilder();
        if (oldSummary != null && !oldSummary.isEmpty()) {
            historyBuilder.append("【之前的对话全局摘要】:\n").append(oldSummary).append("\n\n");
        }

        historyBuilder.append("【以下是最新增加的几轮对话记录】:\n");
        for (ContextWindow.Memory.ConversationTurn turn : history) {
            historyBuilder.append("第").append(turn.getTurn()).append("轮:\n");
            historyBuilder.append("  用户: ").append(turn.getUserQuery()).append("\n");
            historyBuilder.append("  AI: ").append(turn.getAiResponse()).append("\n\n");
        }

        String prompt = historyBuilder.toString() +
                "\n请作为记忆管理专家，根据以上内容，将最新增加的对话重点【融合】到之前的全局摘要中，" +
                "生成一份更新后的全局摘要（不超过150字）。要求：\n" +
                "1. 必须连贯且保留核心上下文信息。\n" +
                "2. 严禁返回任何 markdown 代码块，直接返回纯文本摘要。\n" +
                "3. 严禁回复诸如“好的”之类的废话。";

        try {
            String summary = chatModel.generate(prompt);
            // 防止大模型意外崩溃导致记忆直接被覆盖为空
            if (summary == null || summary.trim().isEmpty()) {
                log.warn("大模型生成摘要为空，已降级保留旧摘要");
                return oldSummary;
            }
            if (summary.startsWith("摘要：") || summary.startsWith("摘要:")) {
                summary = summary.substring(3).trim();
            }
            return summary.trim();
        } catch (Exception e) {
            log.error("生成滚动对话摘要时调用 LLM 失败", e);
            return oldSummary; // 失败时保留旧记忆，避免清空
        }
    }

    /**
     * 加载会话的短期记忆
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @return 上下文窗口
     */
    public ContextWindow loadSessionShortTermMemory(String userId, String sessionId) {
        ContextWindow contextWindow = new ContextWindow();
        contextWindow.getMemory().getLongTerm().setUserId(userId);

        ContextWindow.Memory.LongTermMemory longTerm = userProfileService.loadLongTermMemory(userId);
        if (longTerm != null) {
            contextWindow.getMemory().setLongTerm(longTerm);
        }

        List<com.example.demo02criticalissues.entity.ConversationArchive> history =
                conversationArchiveRepository.findRecentConversations(userId, sessionId, PageRequest.of(0, maxRounds));

        if (!history.isEmpty()) {
            List<ContextWindow.Memory.ConversationTurn> turns = new ArrayList<>();
            for (com.example.demo02criticalissues.entity.ConversationArchive archive : history) {
                turns.add(new ContextWindow.Memory.ConversationTurn(
                        archive.getRoundNum(),
                        archive.getUserQuery(),
                        archive.getAiResponse()
                ));
            }

            if (history.size() >= maxRounds) {
                String summary = generateRollingConversationSummary("", turns);
                contextWindow.setShortTermSummary(summary);
                contextWindow.getMemory().getShortTerm().setRound(history.get(0).getRoundNum());
            } else {
                contextWindow.getMemory().getShortTerm().setHistory(turns);
                contextWindow.getMemory().getShortTerm().setRound(history.get(0).getRoundNum());
            }
        }

        return contextWindow;
    }

    /**
     * 清空短期记忆（开始新会话时调用）
     *
     * @param contextWindow 上下文窗口
     */
    public void clearShortTermMemory(ContextWindow contextWindow) {
        contextWindow.clearShortTermMemory();
        log.info("已清空短期记忆");
    }
}