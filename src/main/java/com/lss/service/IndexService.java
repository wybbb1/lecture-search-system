package com.lss.service;

import com.lss.util.MarkdownProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class IndexService {

    private final LlmSegmenterService llmSegmenterService;
    private final MarkdownProcessor markdownProcessor;
    // 这里会注入倒排索引管理器
    // private final InvertedIndexManager invertedIndexManager;

    public IndexService(LlmSegmenterService llmSegmenterService,
                        MarkdownProcessor markdownProcessor
                        /*, InvertedIndexManager invertedIndexManager*/) {
        this.llmSegmenterService = llmSegmenterService;
        this.markdownProcessor = markdownProcessor;
        // this.invertedIndexManager = invertedIndexManager;
    }

    /**
     * 处理单个Markdown文档，进行分词并准备构建索引。
     * @param markdownFilePath 讲座Markdown文档路径
     * @return 分词后的词项列表
     */
    public List<String> processMarkdownDocumentForIndexing(Path markdownFilePath) {
        String plainTextContent;
        try {
            plainTextContent = markdownProcessor.convertMarkdownToPlainText(markdownFilePath);
            log.info("Successfully converted Markdown {} to plain text.", markdownFilePath.getFileName());
        } catch (IOException e) {
            log.error("Failed to read or convert Markdown file: {}", markdownFilePath, e);
            return Collections.emptyList();
        }

        // 调用大模型API进行分词
        List<String> terms = llmSegmenterService.segmentTextWithLlm(plainTextContent);
        log.info("Segmented document {}. Found {} terms.", markdownFilePath.getFileName(), terms.size());

        // 接下来，您将使用这些terms来构建倒排索引。
        // 例如：
        // LectureDocument doc = new LectureDocument(markdownFilePath.getFileName().toString(), plainTextContent, ...);
        // invertedIndexManager.addDocument(doc, terms);

        return terms;
    }

    /**
     * 批量处理所有文档以构建初始索引。
     * @param documentPaths 所有Markdown文档的路径列表
     */
    public void buildInitialIndex(List<Path> documentPaths) {
        log.info("Starting initial index build for {} documents.", documentPaths.size());
        for (Path path : documentPaths) {
            processMarkdownDocumentForIndexing(path);
            // 在这里可能需要将LectureDocument对象及其terms传递给InvertedIndexManager来构建索引
            // 例如：invertedIndexManager.addDocument(new LectureDocument(...), terms);
        }
        log.info("Initial index build completed.");
        // 构建完成后，可能需要持久化索引
        // invertedIndexManager.persistIndex();
    }
}