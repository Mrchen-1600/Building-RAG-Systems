package com.example.demo02criticalissues.chunker;

import com.example.demo02criticalissues.entity.SentenceMetadata;
import com.example.demo02criticalissues.repository.SentenceMetadataRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ClassName: SentenceWindowChunker
 * Package: com.example.demo02criticalissues.chunker
 *
 * @Author Mrchen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentenceWindowChunker {

    private final SentenceMetadataRepository sentenceMetadataRepository;

    /**
     * 句子窗口切片结果
     */
    public record SentenceWindowChunkResult(
            List<SentenceMetadata> sentences,
            List<TextSegment> segments
    ) {}

    /**
     * 执行句子窗口切片
     *
     * @param document 文档
     * @param docId   文档ID
     * @return 切片结果
     */
    @Transactional
    public SentenceWindowChunkResult chunk(Document document, String docId) {
        List<SentenceMetadata> sentences = new ArrayList<>();
        List<TextSegment> segments = new ArrayList<>();

        String text = document.text();

        // 按句子切分（支持中英文）
        List<String> sentenceTexts = splitIntoSentences(text);

        for (int i = 0; i < sentenceTexts.size(); i++) {
            String sentenceText = sentenceTexts.get(i).trim();
            if (sentenceText.isEmpty()) {
                continue;
            }

            String sentenceVectorId = UUID.randomUUID().toString();

            // 创建句子元数据
            SentenceMetadata metaRecord = new SentenceMetadata();
            metaRecord.setDocId(docId);
            metaRecord.setSentenceIndex(i + 1); // 从1开始
            metaRecord.setSentenceText(sentenceText);
            metaRecord.setSentenceVectorId(sentenceVectorId);

            sentences.add(metaRecord);

            // 创建文本段（用于向量库），注入 Metadata
            // 确保召回后能精准知道属于哪篇文档的第几句，以便去数据库滑动窗口
            Metadata metadata = new Metadata();
            metadata.put("sentenceVectorId", sentenceVectorId);
            metadata.put("docId", docId);
            metadata.put("sentenceIndex", String.valueOf(i + 1));
            metadata.put("chunkType", "sentence_window");
            TextSegment segment = TextSegment.from(sentenceText, metadata);

            segments.add(segment);
        }

        // 保存句子元数据
        sentenceMetadataRepository.saveAll(sentences);

        log.info("句子窗口切片完成 - 文档: {}, 句子数: {}", docId, sentences.size());

        return new SentenceWindowChunkResult(sentences, segments);
    }

    /**
     * 将文本切分为句子
     */
    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();

        // 使用正则表达式切分句子
        // 支持中文标点：。！？；
        // 支持英文标点：. ! ? ;
        // 省略号：... … …
        Pattern pattern = Pattern.compile("([。！？；\\.!?;]|(?:\\.{3})|(?:…{1,2}))\\s*");
        Matcher matcher = pattern.matcher(text);

        int start = 0;
        while (matcher.find()) {
            int end = matcher.end();
            String sentence = text.substring(start, end).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
            start = end;
        }

        // 添加最后一个句子（如果没有结束标点）
        if (start < text.length()) {
            String lastSentence = text.substring(start).trim();
            if (!lastSentence.isEmpty()) {
                sentences.add(lastSentence);
            }
        }

        return sentences;
    }

    /**
     * 获取句子窗口
     *
     * @param docId         文档ID
     * @param sentenceIndex  命中的句子索引
     * @param windowSize    窗口大小（前后各取windowSize-1/2句）
     * @return 窗口文本
     */
    public String getSentenceWindow(String docId, int sentenceIndex, int windowSize) {
        int halfWindow = windowSize / 2;
        int startIndex = Math.max(1, sentenceIndex - halfWindow);
        int endIndex = sentenceIndex + halfWindow;

        List<SentenceMetadata> sentences = sentenceMetadataRepository
                .findByDocIdAndSentenceIndexRange(docId, startIndex, endIndex);

        return sentences.stream()
                .map(SentenceMetadata::getSentenceText)
                .reduce((s1, s2) -> s1 + s2)
                .orElse("");
    }

    /**
     * 从文本段获取句子窗口
     * 优先依赖 Metadata 进行严格匹配，降低碰撞概率
     *
     * @param segment    命中的文本段
     * @param docId      文档ID
     * @param windowSize 窗口大小
     * @return 窗口文本
     */
    public String getSentenceWindowFromSegment(TextSegment segment, String docId, int windowSize) {
        // 1. 优先尝试从 Metadata 解析
        if (segment.metadata() != null) {
            String sentenceVectorId = segment.metadata().getString("sentenceVectorId");
            if (sentenceVectorId != null) {
                Optional<SentenceMetadata> smOpt = sentenceMetadataRepository.findBySentenceVectorId(sentenceVectorId);
                if (smOpt.isPresent()) {
                    return getSentenceWindow(smOpt.get().getDocId(), smOpt.get().getSentenceIndex(), windowSize);
                }
            }
        }

        // 2. 兜底策略：文本暴力匹配
        String segmentText = segment.text();
        List<SentenceMetadata> allSentences = sentenceMetadataRepository
                .findByDocIdOrderBySentenceIndexAsc(docId);

        int targetIndex = -1;
        for (SentenceMetadata sm : allSentences) {
            if (sm.getSentenceText().equals(segmentText)) {
                targetIndex = sm.getSentenceIndex();
                break;
            }
        }

        if (targetIndex == -1) {
            return segmentText; // 如果找不到匹配，返回原始文本
        }

        // 获取窗口
        return getSentenceWindow(docId, targetIndex, windowSize);
    }

    /**
     * 批量获取句子窗口（去重）
     *
     * @param segments   文本段列表
     * @param docId      文档ID
     * @param windowSize 窗口大小
     * @return 去重后的窗口文本列表
     */
    public List<String> getSentenceWindows(List<TextSegment> segments, String docId, int windowSize) {
        List<String> results = new ArrayList<>();
        for (TextSegment segment : segments) {
            String window = getSentenceWindowFromSegment(segment, docId, windowSize);
            if (!results.contains(window)) {
                results.add(window);
            }
        }
        return results;
    }

    /**
     * 获取完整的句子窗口字符串
     *
     * @param segments   文本段列表
     * @param docId      文档ID
     * @param windowSize 窗口大小
     * @return 格式化的窗口文本
     */
    public String getFormattedSentenceWindows(List<TextSegment> segments, String docId, int windowSize) {
        StringBuilder sb = new StringBuilder();

        List<String> windows = getSentenceWindows(segments, docId, windowSize);

        for (int i = 0; i < windows.size(); i++) {
            sb.append("【参考片段").append(i + 1).append("】:\n");
            sb.append(windows.get(i)).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * 获取完整的句子窗口字符串（当无法确定 docId 时使用）
     * 返回原始句子文本，不扩展窗口
     *
     * @param segments   文本段列表
     * @param windowSize 窗口大小（此参数被忽略）
     * @return 格式化的文本
     */
    public String getFormattedSentenceWindowsSimple(List<TextSegment> segments, int windowSize) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < segments.size(); i++) {
            sb.append("【参考片段").append(i + 1).append("】:\n");
            sb.append(segments.get(i).text()).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * 获取句子的前后句
     *
     * @param docId         文档ID
     * @param sentenceIndex  句子索引
     * @param contextSize   上下文大小（前后各取contextSize句）
     * @return 上下文句子
     */
    public List<SentenceMetadata> getContextSentences(String docId, int sentenceIndex, int contextSize) {
        int startIndex = Math.max(1, sentenceIndex - contextSize);
        int endIndex = sentenceIndex + contextSize;

        return sentenceMetadataRepository
                .findByDocIdAndSentenceIndexRange(docId, startIndex, endIndex);
    }
}