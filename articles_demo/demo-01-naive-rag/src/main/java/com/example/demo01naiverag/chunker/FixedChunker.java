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
public class FixedChunker {
    /**
     * 策略 1：固定大小分块：不考虑语义，强制按字符数切分
     */
    public List<TextSegment> chunk(Document document, int chunkSize, int overlap) {
        return DocumentSplitters.recursive(chunkSize, overlap).split(document);
    }
}
