package com.lss.service;

import com.lss.model.ChatDoc.ChatDocResponse;
import com.lss.model.ChatDoc.ChatRequest;
import com.lss.repository.InvertedIndexManager;
import com.lss.model.LectureDocument;
import com.lss.util.MarkdownProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class IndexService {

    private final LLMSegmenterService llmSegmenterService;
    private final InvertedIndexManager invertedIndexManager;

    public IndexService(LLMSegmenterService llmSegmenterService,
                        MarkdownProcessor markdownProcessor
                        , InvertedIndexManager invertedIndexManager) {
        this.llmSegmenterService = llmSegmenterService;
        this.invertedIndexManager = invertedIndexManager;
    }

    /**
     * 处理单个Markdown文档，进行分词并准备构建索引。
     * @param markdownFilePath 讲座Markdown文档路径
     * @return 分词后的词项列表
     */
    public ChatDocResponse processMarkdownDocumentForTerms(Path markdownFilePath) {
        String plainTextContent;
        try {
            plainTextContent = MarkdownProcessor.convertMarkdownToFullText(markdownFilePath);

            log.info("Successfully converted Markdown {} to plain text.", markdownFilePath.getFileName());
        } catch (IOException e) {
            log.error("Failed to read or convert Markdown file: {}", markdownFilePath, e);
            return null;
        }

        // 调用大模型API进行分词
        return llmSegmenterService.segmentTextWithLlm(plainTextContent);
    }

    /**
     * 批量处理所有文档以构建初始索引。
     * @param documentPaths 所有Markdown文档的路径列表
     */
    public void buildInitialIndex(List<Path> documentPaths) throws IOException {
        log.info("Starting initial index build for {} documents.", documentPaths.size());
        for (Path path : documentPaths) {
            ChatDocResponse response = processMarkdownDocumentForTerms(path);
            // 将LectureDocument对象及其terms传递给InvertedIndexManager来构建索引

            String[] fileName = path.getFileName().toString().split("_");
            LectureDocument document = new LectureDocument();
            document.setId(fileName[0]); // 使用文件名作为文档ID
            document.setTitle(fileName[1]);
            document.setOriginalFilePath(path.toString());

            invertedIndexManager.addDocumentToIndex(document, response.getTitleTextTokenized(), "Title");
            invertedIndexManager.addDocumentToIndex(document, response.getFullTextTokenized(), "FullText");
            invertedIndexManager.addDocumentToIndex(document, Collections.singletonList(response.getSpeaker()), "Speaker");
        }
        log.info("Initial index build completed.");
        // 构建完成后，需要持久化索引
        invertedIndexManager.persistIndex();
    }
}