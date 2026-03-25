package com.example.demo01naiverag.parser;

import dev.langchain4j.data.document.Document;
import org.jsoup.Jsoup;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ClassName: HybridRerankService
 * Package: com.example.demo01naiverag.retriever
 *
 * @Author Mrchen
 */
public class HtmlParser {
    /**
     * 解析 HTML 文件，提取其中的纯文本内容，去除标签
     */
    public Document parse(Path filePath) {
        System.out.println("正在解析 HTML 文件: " + filePath.getFileName());
        try {
            String htmlContent = Files.readString(filePath);
            // 提取纯文本
            String cleanText = Jsoup.parse(htmlContent).text();
            return Document.from(cleanText);
        } catch (Exception e) {
            System.err.println("HTML 文件解析失败: " + e.getMessage());
            return Document.from("");
        }
    }
}