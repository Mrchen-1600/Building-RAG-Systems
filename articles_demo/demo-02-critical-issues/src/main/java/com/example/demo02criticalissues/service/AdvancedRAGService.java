package com.example.demo02criticalissues.service;

import com.example.demo02criticalissues.chunker.ParentChildChunker;
import com.example.demo02criticalissues.chunker.SentenceWindowChunker;
import com.example.demo02criticalissues.context.ContextWindow;
import com.example.demo02criticalissues.context.ContextWindowBuilder;
import com.example.demo02criticalissues.entity.RaptorTree;
import com.example.demo02criticalissues.repository.RaptorTreeRepository;
import com.example.demo02criticalissues.retriever.HybridRerankService;
import com.example.demo02criticalissues.retriever.IRCoTRetriever;
import com.example.demo02criticalissues.router.LLMIntentRouter;
import com.example.demo02criticalissues.router.SemanticIntentRouter;
import com.example.demo02criticalissues.tools.Text2SqlTool;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ClassName: AdvancedRAGService
 * Package: com.example.demo02criticalissues.service
 *
 * @Author Mrchen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdvancedRAGService {

    private final ChatLanguageModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    private final LLMIntentRouter llmIntentRouter;
    private final SemanticIntentRouter semanticIntentRouter;
    private final HybridRerankService hybridRerankService;
    private final IRCoTRetriever icoTRetriever;
    private final HyDEService hydeService;
    private final RAPTORService raptorService;
    private final SelfRAGService selfRagService;
    private final QueryRewriteService queryRewriteService;
    private final ShortTermMemoryService shortTermMemoryService;
    private final UserProfileService userProfileService;
    private final ConversationArchiveService conversationArchiveService;
    private final Text2SqlTool text2SqlTool;
    private final ParentChildChunker parentChildChunker;
    private final SentenceWindowChunker sentenceWindowChunker;
    private final RaptorTreeRepository raptorTreeRepository;

    @Value("${retrieval.top-k:5}")
    private int topK;

    @Value("${intent-routing.enable-llm-router:true}")
    private boolean useLLMRouter;

    @Value("${intent-routing.enable-semantic-router:false}")
    private boolean useSemanticRouter;

    public record RAGResponse(
            String answer,
            String reasoning,
            String retrievalStrategy,
            List<String> usedTools
    ) {}

    /**
     * 提供一个纯朴素的 RAG 流程，专门用于测试对比
     * 流程：拿到原始问题 -> 直接检索向量库 -> 直接拼凑系统 prompt -> LLM强行生成
     */
    public String processNaiveRAG(String query) {
        List<TextSegment> segments = retrieveDocuments(query);
        String retrievedContext = segments.stream()
                .map(TextSegment::text)
                .collect(Collectors.joining("\n---\n"));

        String naivePrompt = "你是一个企业内部问答助手。请严格基于以下【参考资料】的内容回答用户的问题。" +
                "如果参考资料中没有包含能够回答该问题的信息，请直接回答“根据参考资料，无法回答该问题”，严禁使用你自身的先验知识进行脑补！\n\n" +
                "【参考资料】：\n" + (retrievedContext.isEmpty() ? "（无资料）" : retrievedContext) + "\n\n" +
                "【用户问题】：" + query + "\n\n请直接给出答案：";

        try {
            return chatModel.generate(naivePrompt);
        } catch (Exception e) {
            return "Naive RAG 生成失败：" + e.getMessage();
        }
    }

    public RAGResponse processQuery(String userQuery, String userId, String sessionId) {
        long startTime = System.currentTimeMillis();

        try {
            ContextWindow contextWindow = shortTermMemoryService.loadSessionShortTermMemory(userId, sessionId);
            // 将用户的原始提问写入 ContextWindow
            contextWindow.setInput(userQuery);

            LLMIntentRouter.RouteResult routeResult = routeIntent(userQuery);
            log.info("意图路由结果: {} - {}", routeResult.intentType(), routeResult.reasoning());

            String finalAnswer;
            String retrievalStrategy = routeResult.intentType().name();
            List<String> usedTools = List.of("IntentRouting");

            switch (routeResult.intentType()) {
                case STRUCTURED_QUERY:
                    finalAnswer = handleStructuredQuery(routeResult.rewrittenQuery(), contextWindow);
                    usedTools = List.of("IntentRouting", "Text2SQL");
                    break;
                case CHAT:
                    finalAnswer = handleChat(userQuery, contextWindow);
                    usedTools = List.of("IntentRouting");
                    break;
                case COMPARISON_RETRIEVAL:
                    finalAnswer = handleComparisonRetrieval(routeResult.rewrittenQuery(), contextWindow);
                    usedTools = List.of("IntentRouting", "IRCoT");
                    break;
                case SUMMARY_QUERY:
                    finalAnswer = handleSummaryQuery(routeResult.rewrittenQuery(), contextWindow);
                    usedTools = List.of("IntentRouting", "RAPTOR");
                    break;
                case COMPLEX_TASK:
                    finalAnswer = handleComplexTask(routeResult.rewrittenQuery(), contextWindow);
                    usedTools = List.of("IntentRouting", "HyDE");
                    break;
                case VECTOR_SEARCH:
                default:
                    finalAnswer = handleVectorSearch(routeResult.rewrittenQuery(), contextWindow);
                    usedTools = List.of("IntentRouting", "VectorSearch", "QueryRewrite", "HybridRerank", "SelfRAG");
                    break;
            }

            shortTermMemoryService.updateShortTermMemory(contextWindow, userQuery, finalAnswer);

            int roundNum = conversationArchiveService.getNextRoundNum(sessionId);
            conversationArchiveService.archiveConversation(
                    userId, sessionId, roundNum,
                    userQuery, routeResult.rewrittenQuery(),
                    contextWindow.getRetrievedContext(),
                    finalAnswer, routeResult.intentType().name(),
                    retrievalStrategy, usedTools,
                    (int)(System.currentTimeMillis() - startTime)
            );

            return new RAGResponse(finalAnswer, routeResult.reasoning(), retrievalStrategy, usedTools);

        } catch (Exception e) {
            log.error("处理用户查询失败", e);
            return new RAGResponse("抱歉，处理您的问题时发生了错误，请稍后重试。", "处理失败: " + e.getMessage(), "ERROR", List.of());
        }
    }

    private LLMIntentRouter.RouteResult routeIntent(String userQuery) {
        if (useLLMRouter) {
            return llmIntentRouter.route(userQuery);
        } else if (useSemanticRouter) {
            SemanticIntentRouter.SemanticRouteResult result = semanticIntentRouter.route(userQuery);
            return new LLMIntentRouter.RouteResult(result.intentType(), result.reasoning(), userQuery);
        } else {
            return new LLMIntentRouter.RouteResult(LLMIntentRouter.IntentType.VECTOR_SEARCH, "未启用路由，使用默认向量检索", userQuery);
        }
    }

    /**
     * 将执行结果作为 retrievedContext 放入 ContextWindow，使用 buildPrompt 回答
     */
    private String handleStructuredQuery(String query, ContextWindow contextWindow) {
        Text2SqlTool.Text2SqlResult result = text2SqlTool.execute(query);
        if (result.success()) {
            contextWindow.setRetrievedContext("【底层动作执行结果】\n" + result.formatResultText() + "\n请用自然、专业的语言向用户汇报结果。");
            return chatModel.generate(contextWindow.buildPrompt());
        } else {
            return "【执行失败】 " + result.message();
        }
    }

    /**
     * 闲聊，也需要依赖上下文记忆和核心规则
     */
    private String handleChat(String userQuery, ContextWindow contextWindow) {
        contextWindow.setRetrievedContext("【当前环境为自由对话，无外部文档检索】");
        return chatModel.generate(contextWindow.buildPrompt());
    }

    private String handleComparisonRetrieval(String query, ContextWindow contextWindow) {
        IRCoTRetriever.IRCoTResult result = icoTRetriever.retrieve(query);
        StringBuilder contextBuilder = new StringBuilder();
        for (IRCoTRetriever.RetrievalStep step : result.steps()) {
            for (TextSegment doc : step.retrievedDocs()) {
                contextBuilder.append(doc.text()).append("\n\n");
            }
        }
        contextWindow.setRetrievedContext(contextBuilder.toString());
        // IRCoT 内部可能已经生成了答案，但也使用带记忆的 ContextWindow 重新润色
        contextWindow.setInput("请根据以下经过逻辑推理搜索出的内容回答：" + query);
        return chatModel.generate(contextWindow.buildPrompt());
    }

    private String handleSummaryQuery(String query, ContextWindow contextWindow) {
        String targetDocId = "doc_raptor_001";

        try {
            Optional<RaptorTree> rootNode = raptorTreeRepository.findFirstByNodeType("root");
            if (rootNode.isPresent()) {
                targetDocId = rootNode.get().getDocId();
            }
        } catch (Exception e) {
            log.warn("无法从数据库读取动态RAPTOR树，将使用默认文档ID", e);
        }

        if (raptorService.isSuitableForRAPTOR(query)) {
            RAPTORService.RAPTORResult result = raptorService.retrieve(targetDocId, query);

            if (!result.summaries().isEmpty()) {
                StringBuilder context = new StringBuilder();
                for (String summary : result.summaries()) {
                    context.append(summary).append("\n\n");
                }

                // 依托 ContextWindow 进行回答
                contextWindow.setRetrievedContext("【宏观摘要库】\n" + context.toString());
                return chatModel.generate(contextWindow.buildPrompt());
            }
        }

        log.warn("RAPTOR无法找到摘要树，降级回退到普通检索");
        return handleVectorSearch(query, contextWindow);
    }

    private String handleComplexTask(String query, ContextWindow contextWindow) {
        if (hydeService.shouldUseHyDE(query)) {
            HyDEService.HyDEResult result = hydeService.executeHyDE(query, topK);
            StringBuilder contextBuilder = new StringBuilder();
            for (TextSegment segment : result.retrievedSegments()) {
                contextBuilder.append(segment.text()).append("\n\n");
            }

            // 依托 ContextWindow 进行回答
            contextWindow.setRetrievedContext("【HyDE 增强检索库】\n" + contextBuilder.toString());
            return chatModel.generate(contextWindow.buildPrompt());
        } else {
            return handleVectorSearch(query, contextWindow);
        }
    }

    private String handleVectorSearch(String query, ContextWindow contextWindow) {
        QueryRewriteService.RewriteResult rewriteResult = queryRewriteService.hybridRewrite(query, contextWindow, "system");
        String effectiveQuery = rewriteResult.rewrittenQuery();

        SelfRAGService.SelfRAGResult ragResult = selfRagService.execute(
                effectiveQuery,
                q -> hybridRerankService.searchAndRerank(q, topK),
                (q, docs) -> generateAnswer(q, docs, contextWindow)
        );

        StringBuilder contextBuilder = new StringBuilder();
        List<TextSegment> finalDocs = ragResult.finalRetrievedDocs();
        if (finalDocs != null) {
            for (int i = 0; i < finalDocs.size(); i++) {
                contextBuilder.append("【参考片段").append(i + 1).append("】:").append(finalDocs.get(i).text()).append("\n");
            }
        }
        contextWindow.setRetrievedContext(contextBuilder.toString());
        return ragResult.answer();
    }

    public List<TextSegment> retrieveDocuments(String query) {
        try {
            Embedding embedding = embeddingModel.embed(query).content();
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder().queryEmbedding(embedding).maxResults(topK).build();
            return embeddingStore.search(searchRequest).matches().stream().map(EmbeddingMatch::embedded).toList();
        } catch (Exception e) {
            log.error("检索文档失败", e);
            return List.of();
        }
    }

    /**
     * 启用 ContextWindow 的系统记忆构建能力
     */
    public String generateAnswer(String query, List<TextSegment> retrievedDocs, ContextWindow contextWindow) {
        StringBuilder contextBuilder = new StringBuilder();
        if (retrievedDocs == null || retrievedDocs.isEmpty()) {
            contextBuilder.append("（空，没有检索到任何有关参考资料）");
        } else {
            for (int i = 0; i < retrievedDocs.size(); i++) {
                contextBuilder.append("【参考片段").append(i + 1).append("】: ").append(retrievedDocs.get(i).text()).append("\n");
            }
        }

        // 把检索到的核心资料推入 ContextWindow 的 retrievedContext 槽位
        contextWindow.setRetrievedContext(contextBuilder.toString());

        // 调用 chatModel 生成答案时，提供整个 ContextWindow 编译后的完整 Prompt（包含长短期记忆与系统指令）
        return chatModel.generate(contextWindow.buildPrompt());
    }

    public List<TextSegment> retrieveWithParentChild(String query) {
        List<TextSegment> childSegments = retrieveDocuments(query);
        List<String> parentTexts = parentChildChunker.getParentsFromSegments(childSegments);
        return parentTexts.stream().map(TextSegment::from).toList();
    }

    public List<TextSegment> retrieveWithSentenceWindow(String query) {
        List<TextSegment> sentenceSegments = retrieveDocuments(query);
        String formattedWindows = sentenceWindowChunker.getFormattedSentenceWindowsSimple(sentenceSegments, 5);
        return List.of(TextSegment.from(formattedWindows));
    }

    public ContextWindow buildContextWindow(String userId, String sessionId, String userInput) {
        return ContextWindowBuilder.from(shortTermMemoryService.loadSessionShortTermMemory(userId, sessionId)).input(userInput).userId(userId).build();
    }
}