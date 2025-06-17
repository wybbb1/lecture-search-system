package com.lss.service;

import com.lss.model.ChatDoc.ChatDocResponse;
import com.lss.model.InvertedIndex;
import com.lss.repository.InvertedIndexManager;
import com.lss.model.LectureDocument;
import com.lss.util.MarkdownProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Service
@Slf4j
public class IndexService {

    private final LLMSegmenterService llmSegmenterService;
    private final InvertedIndexManager invertedIndexManager;

    public IndexService(LLMSegmenterService llmSegmenterService
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
     *
     * @param documentPaths      所有Markdown文档的路径列表
     * @param indexingThreadPool
     */
    public void buildInitialIndex(List<Path> documentPaths, Boolean forceRebuild, ExecutorService indexingThreadPool) throws IOException {
        log.info("Starting initial index build for {} documents.", documentPaths.size());

        InvertedIndex currentInvertedIndex = invertedIndexManager.getInvertedIndex();

        // 判断是否需要重建
        // 只有当 forceRebuild 为 true，或者当前索引为空，或者文档数量不匹配时才重建
        if (!forceRebuild &&
                currentInvertedIndex.getTotalDocuments() > 0 &&
                currentInvertedIndex.getTotalDocuments() == documentPaths.size()) {
            log.info("Existing index already contains {} documents, matching current data set size. Skipping full rebuild.", currentInvertedIndex.getTotalDocuments());
            return;
        }

        // 清空旧索引，开始重建
        currentInvertedIndex.getDictionary().clear();
        currentInvertedIndex.getDocumentStore().clear();
        currentInvertedIndex.getDocumentFrequencies().clear();

        buildIndexForAllDoc(documentPaths, indexingThreadPool);

        // 构建完成后，需要持久化索引
        invertedIndexManager.persistIndex();
    }

    /**
     * 增量添加新文档到现有索引。
     * 此方法会处理新增文档，并将其加入到当前加载的索引中，然后持久化。
     *
     * @param newDocumentPaths 新增Markdown文档的路径列表
     * @param indexingThreadPool 用于异步处理文档的线程池
     */
    public void addIncrementalDocuments(List<Path> newDocumentPaths, ExecutorService indexingThreadPool) {
        if (newDocumentPaths == null || newDocumentPaths.isEmpty()) {
            log.info("No new documents to add incrementally.");
            return;
        }

        log.info("Starting incremental index update for {} new documents.", newDocumentPaths.size());

        // 不需要清空索引，直接添加到现有加载的索引中
        // InvertedIndex currentInvertedIndex = invertedIndexManager.getInvertedIndex();

        // 异步处理新文档，并添加到现有索引
        buildIndexForAllDoc(newDocumentPaths, indexingThreadPool);

        // 持久化更新后的索引
        invertedIndexManager.persistIndex();

    }

    private void buildIndexForAllDoc(List<Path> documentPaths, ExecutorService indexingThreadPool) {

        List<Future<?>> futures = new ArrayList<>();
        for (Path path : documentPaths) {
            futures.add(indexingThreadPool.submit(() -> {
                ChatDocResponse response = processMarkdownDocumentForTerms(path);

                String[] fileName = path.getFileName().toString().split("_");
                LectureDocument document = new LectureDocument();
                document.setId(fileName[0]);
                document.setTitle(fileName[1]);
                document.setOriginalFilePath(path.toString());

                invertedIndexManager.addDocumentToIndex(document, response.getTitleTextTokenized(), "Title");
                invertedIndexManager.addDocumentToIndex(document, response.getFullTextTokenized(), "FullText");
                invertedIndexManager.addDocumentToIndex(document, Collections.singletonList(response.getSpeaker()), "Speaker");

            }));
        }
        log.info("Initial index build completed.");

        for (Future<?> future : futures) {
            try {
                future.get(); // 阻塞直到任务完成，如果任务抛出异常，这里会再次抛出
            } catch (Exception e) {
                log.error("Error during asynchronous document processing: {}", e.getMessage(), e);
            }
        }
    }

}

