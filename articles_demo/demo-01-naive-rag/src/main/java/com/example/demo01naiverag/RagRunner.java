package com.example.demo01naiverag;

import com.example.demo01naiverag.chunker.FixedChunker;
import com.example.demo01naiverag.chunker.RecursiveChunker;
import com.example.demo01naiverag.chunker.SemanticChunker;
import com.example.demo01naiverag.parser.HtmlParser;
import com.example.demo01naiverag.parser.MarkdownParser;
import com.example.demo01naiverag.parser.WordParser;
import com.example.demo01naiverag.retriever.HybridRerankService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Component
public class RagRunner implements CommandLineRunner {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final HybridRerankService retriever;
    private final QwenChatModel chatModel;

    @Value("${chunking.fixed.chunk-size:200}")
    private int fixedChunkSize;
    @Value("${chunking.fixed.overlap:30}")
    private int fixOverlap;

    @Value("${chunking.recursive.max-chunk-size:200}")
    private int recursiveMaxChunkSize;
    @Value("${chunking.recursive.overlap-size:30}")
    private int recursiveOverlapSize;

    @Value("${chunking.semantic.similarity-threshold:0.7}")
    private double semanticThreshold;


    @Value("${retrieval.top-k:5}")
    private int topK;

    @Value("${system.project-dir}")
    private String projectDir;

    @Value("${system.wait-time-ms:2000}")
    private int waitTimeMs;

    @Value("${system.user-query}")
    private String userQuery;

    @Value("${file.markdown.markdown-name}")
    private String mdName;
    @Value("${file.docx.docx-name}")
    private String docxName;
    @Value("${file.html.html-name}")
    private String htmlName;

    public RagRunner(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            HybridRerankService retriever,
            QwenChatModel chatModel) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.retriever = retriever;
        this.chatModel = chatModel;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("========== 启动 RAG 标准处理流程 Demo ==========");


        System.out.println("\n--- [第一阶段：离线解析与建库] ---");
        List<Document> allDocuments = new ArrayList<>();

        try {
            Path resourcePath = Paths.get(projectDir, "src", "main", "resources", "files");
            if (!Files.exists(resourcePath)) {
                System.err.println("❌ 找不到指定的资源目录！");
                return;
            }
            allDocuments.add(new MarkdownParser().parse(resourcePath.resolve(mdName)));
            allDocuments.add(new HtmlParser().parse(resourcePath.resolve(htmlName)));
            allDocuments.add(new WordParser().parse(resourcePath.resolve(docxName)));
        } catch (Exception e) {
            System.err.println("读取文件出错：" + e.getMessage());
        }

        if (allDocuments.isEmpty()) {
            System.err.println("❌ 未能成功加载任何文档！");
            return;
        }

        // 测试不同的分块策略 (以混合文档为例，这里使用递归分块演示建库)
        System.out.println("\n[测试三种分块策略效果]：");
        Document mdDoc = allDocuments.get(0);

        System.out.println(">> 策略A 固定分块 (20字符): " + new FixedChunker().chunk(mdDoc, 20, 0).size() + " 块");
        System.out.println(">> 策略B 递归分块 (50字符,10重叠): " + new RecursiveChunker().chunk(mdDoc, 50, 10).size() + " 块");
        System.out.println(">> 策略C 语义分块 (阈值0.8): " + new SemanticChunker().chunk(mdDoc, embeddingModel, 0.8).size() + " 块");


        List<TextSegment> allSegments = new ArrayList<>();
//        // 固定大小分块
//        FixedChunker chunker = new FixedChunker();
//        for (Document doc : allDocuments) {
//            allSegments.addAll(chunker.chunk(doc, fixedChunkSize, fixOverlap));
//        }

        // 基于规则的递归分块
        RecursiveChunker chunker = new RecursiveChunker();
        for (Document doc : allDocuments) {
            allSegments.addAll(chunker.chunk(doc, recursiveMaxChunkSize, recursiveOverlapSize));
        }

//        // 基于语意的分块
//        SemanticChunker chunker = new SemanticChunker();
//        for (Document doc : allDocuments) {
//            allSegments.addAll(chunker.chunk(doc, embeddingModel, semanticThreshold));
//        }

        System.out.println("\n正在将 " + allSegments.size() + " 个文本块向量化并存入 Elasticsearch / 内存...");
        List<Embedding> embeddings = embeddingModel.embedAll(allSegments).content();
        embeddingStore.addAll(embeddings, allSegments);

        System.out.println("写入完毕！强制等待 " + waitTimeMs + " 毫秒，等待 Elasticsearch 索引刷新生效...");
        Thread.sleep(waitTimeMs);
        System.out.println("建库完成！");

        System.out.println("\n--- [第二阶段：在线检索与生成] ---");
        System.out.println("用户问题: " + userQuery);

        List<TextSegment> topKDocs = retriever.searchAndRerank(userQuery, topK);

        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < topKDocs.size(); i++) {
            contextBuilder.append("【参考片段").append(i + 1).append("】: ")
                    .append(topKDocs.get(i).text()).append("\n");
        }
        String context = contextBuilder.toString();
        System.out.println("\n构建的上下文 (Context):\n" + (context.isEmpty() ? "（空）" : context));

        String promptTemplate = "你是一个极其严谨的知识库问答助手。请严格遵守以下规则：\n" +
                "1. 你只能基于【参考资料】中提供的内容来回答。\n" +
                "2. 如果【参考资料】为空，或者从中找不到答案，你必须严格回复：'抱歉，知识库中没有包含此问题的相关信息。'，绝不允许使用你自己的外部知识！\n\n" +
                "【参考资料】:\n%s\n\n" +
                "【用户问题】: %s";
        String finalPrompt = String.format(promptTemplate, context, userQuery);

        System.out.println("\n调用大模型生成答案中...");
        String answer = chatModel.generate(finalPrompt);
        System.out.println("\n🤖 AI 最终回答：\n" + answer);
    }
}
