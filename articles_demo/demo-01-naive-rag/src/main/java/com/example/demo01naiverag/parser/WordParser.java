package com.example.demo01naiverag.parser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;

import java.nio.file.Path;

/**
 * ClassName: HybridRerankService
 * Package: com.example.demo01naiverag.retriever
 *
 * @Author Mrchen
 */
public class WordParser {
    /**
     * 解析 Word 文件
     */
    public Document parse(Path filePath) {
        System.out.println("正在解析 Word 文件: " + filePath.getFileName());
        try {
            return FileSystemDocumentLoader.loadDocument(filePath, new ApachePoiDocumentParser());
        } catch (Exception e) {
            System.err.println("Word 文件解析失败，可能文件不存在: " + e.getMessage());
            return Document.from("");
        }
    }
}