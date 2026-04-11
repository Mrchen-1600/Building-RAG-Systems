package com.example.demo02criticalissues.chunker;

import com.example.demo02criticalissues.entity.ParentChildChunk;
import com.example.demo02criticalissues.repository.ParentChildChunkRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ClassName: ParentChildChunker
 * Package: com.example.demo02criticalissues.chunker
 *
 * @Author Mrchen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParentChildChunker {

    private final ParentChildChunkRepository parentChildChunkRepository;

    @Value("${chunking.parent-child.parent-max-size:1000}")
    private int parentMaxSize;

    @Value("${chunking.parent-child.child-max-size:200}")
    private int childMaxSize;

    /**
     * 父子块切片结果
     */
    public record ParentChildChunkResult(
            List<ParentChildChunk> chunks,
            List<TextSegment> childSegments
    ) {}

    /**
     * 执行父子块切片
     *
     * @param document 文档
     * @param docId   文档ID
     * @return 切片结果
     */
    @Transactional
    public ParentChildChunkResult chunk(Document document, String docId) {
        List<ParentChildChunk> chunks = new ArrayList<>();
        List<TextSegment> childSegments = new ArrayList<>();

        String text = document.text();

        // 先按段落切分父块
        List<String> parentChunks = splitIntoParentChunks(text);

        int totalChildIndex = 0;

        for (String parentText : parentChunks) {
            String parentId = UUID.randomUUID().toString();

            // 将每个父块切分为子块
            List<String> childTexts = splitIntoChildChunks(parentText);

            for (int i = 0; i < childTexts.size(); i++) {
                String childId = UUID.randomUUID().toString();
                String childText = childTexts.get(i);

                // 创建父子块映射记录
                ParentChildChunk chunk = new ParentChildChunk();
                chunk.setDocId(docId);
                chunk.setParentId(parentId);
                chunk.setParentText(parentText);
                chunk.setChildId(childId);
                chunk.setChildText(childText);
                chunk.setChunkIndex(i);

                chunks.add(chunk);

                // 创建子块文本段（用于向量库），注入 Metadata
                // 这是为了在召回时能够精准定位到数据库中的对应父块，避免纯文本模糊匹配
                Metadata metadata = new Metadata();
                metadata.put("childId", childId);
                metadata.put("docId", docId);
                metadata.put("chunkType", "parent_child");
                TextSegment childSegment = TextSegment.from(childText, metadata);

                childSegments.add(childSegment);
                totalChildIndex++;
            }
        }

        // 保存父子块映射到数据库
        parentChildChunkRepository.saveAll(chunks);

        log.info("父子块切片完成 - 文档: {}, 父块: {}, 子块: {}", docId, parentChunks.size(), totalChildIndex);

        return new ParentChildChunkResult(chunks, childSegments);
    }

    /**
     * 按段落切分父块
     */
    private List<String> splitIntoParentChunks(String text) {
        List<String> parentChunks = new ArrayList<>();

        // 先按双换行符（段落）切分
        String[] paragraphs = text.split("\n\n");

        StringBuilder currentParent = new StringBuilder();

        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) {
                continue;
            }

            if (currentParent.length() + paragraph.length() <= parentMaxSize) {
                if (!currentParent.isEmpty()) {
                    currentParent.append("\n\n");
                }
                currentParent.append(paragraph);
            } else {
                if (!currentParent.isEmpty()) {
                    parentChunks.add(currentParent.toString());
                }
                currentParent = new StringBuilder(paragraph);

                // 如果单个段落超过父块大小，进一步切分
                while (currentParent.length() > parentMaxSize) {
                    String chunk = currentParent.substring(0, parentMaxSize);
                    parentChunks.add(chunk);
                    currentParent = new StringBuilder(currentParent.substring(parentMaxSize));
                }
            }
        }

        if (!currentParent.isEmpty()) {
            parentChunks.add(currentParent.toString());
        }

        return parentChunks;
    }

    /**
     * 切分子块
     */
    private List<String> splitIntoChildChunks(String parentText) {
        List<String> childChunks = new ArrayList<>();

        // 按句子切分
        String[] sentences = parentText.split("(?<=[。！？.!?])\\s*");

        StringBuilder currentChild = new StringBuilder();

        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) {
                continue;
            }

            if (currentChild.length() + sentence.length() <= childMaxSize) {
                currentChild.append(sentence);
            } else {
                if (!currentChild.isEmpty()) {
                    childChunks.add(currentChild.toString());
                }
                currentChild = new StringBuilder(sentence);

                // 如果单个句子超过子块大小，强制切分
                while (currentChild.length() > childMaxSize) {
                    String chunk = currentChild.substring(0, childMaxSize);
                    childChunks.add(chunk);
                    currentChild = new StringBuilder(currentChild.substring(childMaxSize));
                }
            }
        }

        if (!currentChild.isEmpty()) {
            childChunks.add(currentChild.toString());
        }

        return childChunks;
    }

    /**
     * 根据子块ID获取父块
     *
     * @param childId 子块ID
     * @return 父块文本
     */
    public String getParentByChildId(String childId) {
        return parentChildChunkRepository.findByChildId(childId)
                .map(ParentChildChunk::getParentText)
                .orElse(null);
    }

    /**
     * 根据向量库ID获取父块
     *
     * @param childVectorId 子块向量库ID
     * @return 父块文本
     */
    public String getParentByChildVectorId(String childVectorId) {
        return parentChildChunkRepository.findByChildVectorId(childVectorId)
                .map(ParentChildChunk::getParentText)
                .orElse(null);
    }

    /**
     * 批量获取父块（去重）
     *
     * @param childIds 子块ID列表
     * @return 去重后的父块列表
     */
    public List<String> getParents(List<String> childIds) {
        return childIds.stream()
                .distinct()
                .map(this::getParentByChildId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * 从子块文本段列表获取对应的父块
     * 优先使用 Metadata 中的精准 ID 查询，若丢失则退化为文本匹配查询
     *
     * @param childSegments 子块文本段列表
     * @return 父块文本列表
     */
    public List<String> getParentsFromSegments(List<TextSegment> childSegments) {
        List<String> parentTexts = new ArrayList<>();
        for (TextSegment segment : childSegments) {
            // 1. 尝试从 Metadata 获取强关联的 ID
            String childId = segment.metadata() != null ? segment.metadata().getString("childId") : null;

            if (childId != null) {
                String parentText = getParentByChildId(childId);
                if (parentText != null && !parentTexts.contains(parentText)) {
                    parentTexts.add(parentText);
                }
            } else {
                // 2. 兜底方案：退化为数据库中的文本匹配
                String childText = segment.text();
                List<ParentChildChunk> matchingChunks = parentChildChunkRepository.findByChildText(childText);
                for (ParentChildChunk chunk : matchingChunks) {
                    String parentText = chunk.getParentText();
                    if (!parentTexts.contains(parentText)) {
                        parentTexts.add(parentText);
                    }
                }
            }
        }
        return parentTexts;
    }
}