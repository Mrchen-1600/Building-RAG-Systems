package com.example.demo01naiverag.parser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;

import java.nio.file.Path;

/**
 * ClassName: HybridRerankService
 * Package: com.example.demo01naiverag.retriever
 *
 * @Author Mrchen
 */
public class MarkdownParser {
    /**
     * 解析 Markdown / 纯文本文件
     */
    public Document parse(Path filePath) {
        // LangChain4j 默认的文件加载器能原生提取纯文本及 Markdown
        System.out.println("正在解析 Markdown 文件: " + filePath.getFileName());
        return FileSystemDocumentLoader.loadDocument(filePath);
    }
}