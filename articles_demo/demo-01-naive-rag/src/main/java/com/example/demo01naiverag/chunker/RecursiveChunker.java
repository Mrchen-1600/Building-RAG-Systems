package com.example.demo01naiverag.chunker;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;


/**
 * ClassName: HybridRerankService
 * Package: com.example.demo01naiverag.retriever
 *
 * @Author Mrchen
 */
public class RecursiveChunker {
    /**
     * 策略 2：基于规则的递归分块 (最常用)：优先按段落切分，不行再按句子，最后按字符，带有重叠区(Overlap)防止语义丢失
     */
    public List<TextSegment> chunk(Document document, int maxChunkSize, int overlapSize) {
        return DocumentSplitters.recursive(maxChunkSize, overlapSize).split(document);
    }
}
