package com.example.demo01naiverag.chunker;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;

import java.util.ArrayList;
import java.util.List;

/**
 * ClassName: HybridRerankService
 * Package: com.example.demo01naiverag.retriever
 *
 * @Author Mrchen
 */
public class SemanticChunker {

    /**
     * 策略 3：基于语义的分块：先按标点拆成句子，利用 Embedding 计算相邻句子相似度，发生“话题转折”（相似度低于阈值）时切断。
     */
    public List<TextSegment> chunk(Document document, EmbeddingModel embeddingModel, double similarityThreshold) {
        String text = document.text();
        // 1. 粗略按句号、叹号、问号、换行切分句子
        String[] sentences = text.split("(?<=[。！？\n])");

        List<TextSegment> finalChunks = new ArrayList<>();
        if (sentences.length == 0) return finalChunks;

        StringBuilder currentChunk = new StringBuilder(sentences[0]);
        Embedding currentEmbedding = embeddingModel.embed(sentences[0]).content();

        for (int i = 1; i < sentences.length; i++) {
            String nextSentence = sentences[i].trim();
            if (nextSentence.isEmpty()) continue;

            // 2. 将下一句话向量化
            Embedding nextEmbedding = embeddingModel.embed(nextSentence).content();

            // 3. 计算余弦相似度
            double similarity = CosineSimilarity.between(currentEmbedding, nextEmbedding);

            // 4. 判断语义是否发生转折
            if (similarity >= similarityThreshold) {
                // 讲的是同一件事，合并
                currentChunk.append(nextSentence);
                // 更新当前块的向量特征
                currentEmbedding = nextEmbedding;
            } else {
                // 语义转折，断开生成新 Chunk
                finalChunks.add(TextSegment.from(currentChunk.toString(), document.metadata()));
                currentChunk = new StringBuilder(nextSentence);
                currentEmbedding = nextEmbedding;
            }
        }
        finalChunks.add(TextSegment.from(currentChunk.toString(), document.metadata()));
        return finalChunks;
    }
}