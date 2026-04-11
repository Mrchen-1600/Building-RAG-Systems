package com.example.demo02criticalissues.controller;

import com.example.demo02criticalissues.service.AdvancedRAGService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 *  ClassName: RAGController
 *  Package: com.example.demo02criticalissues.controller
 *
 *  @Author Mrchen
 */
@Slf4j
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RAGController {

    private final AdvancedRAGService ragService;

    /**
     * 聊天接口
     *
     * @param request 聊天请求
     * @return 聊天响应
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("收到聊天请求 - 用户: {}, 问题: {}", request.userId(), request.query());

        try {
            String sessionId = request.sessionId() != null ? request.sessionId() : generateSessionId();

            AdvancedRAGService.RAGResponse response = ragService.processQuery(
                    request.query(),
                    request.userId(),
                    sessionId
            );

            return ResponseEntity.ok(new ChatResponse(
                    response.answer(),
                    sessionId,
                    response.reasoning(),
                    response.retrievalStrategy(),
                    response.usedTools()
            ));

        } catch (Exception e) {
            log.error("聊天处理失败", e);
            return ResponseEntity.internalServerError().body(new ChatResponse(
                    "抱歉，处理您的问题时发生了错误。",
                    null,
                    "处理失败: " + e.getMessage(),
                    "ERROR",
                    List.of()
            ));
        }
    }

    /**
     * 意图分析接口
     *
     * @param request 意图分析请求
     * @return 意图分析响应
     */
    @PostMapping("/intent")
    public ResponseEntity<IntentResponse> analyzeIntent(@RequestBody IntentRequest request) {
        // 这个接口可以用于单独测试意图路由功能
        return ResponseEntity.ok(new IntentResponse(
                "VECTOR_SEARCH",
                "默认返回向量检索意图"
        ));
    }

    /**
     * 查询重写接口
     *
     * @param request 查询重写请求
     * @return 查询重写响应
     */
    @PostMapping("/rewrite")
    public ResponseEntity<RewriteResponse> rewriteQuery(@RequestBody RewriteRequest request) {
        // 这个接口可以用于单独测试查询重写功能

        return ResponseEntity.ok(new RewriteResponse(
                request.query(),
                request.query(),
                List.of(),
                false
        ));
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("OK", "RAG服务正常运行"));
    }

    /**
     * 生成会话ID
     */
    private String generateSessionId() {
        return "session_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
    }

    /**
     * 聊天请求
     */
    public record ChatRequest(
            String userId,
            String sessionId,
            String query
    ) {}

    /**
     * 聊天响应
     */
    public record ChatResponse(
            String answer,
            String sessionId,
            String reasoning,
            String retrievalStrategy,
            List<String> usedTools
    ) {}

    /**
     * 意图请求
     */
    public record IntentRequest(
            String query,
            String userId
    ) {}

    /**
     * 意图响应
     */
    public record IntentResponse(
            String intentType,
            String reasoning
    ) {}

    /**
     * 重写请求
     */
    public record RewriteRequest(
            String query,
            String userId,
            String context
    ) {}

    /**
     * 重写响应
     */
    public record RewriteResponse(
            String originalQuery,
            String rewrittenQuery,
            List<String> changes,
            boolean modified
    ) {}

    /**
     * 健康检查响应
     */
    public record HealthResponse(
            String status,
            String message
    ) {}
}
