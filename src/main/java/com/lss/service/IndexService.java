package com.lss.service;

import com.lss.model.Chat.ChatResponse;
import com.lss.model.Index.InvertedIndex;
import com.lss.repository.InvertedIndexManager;
import com.lss.model.Index.LectureDocument;
import com.lss.util.MarkdownProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Service
@Slf4j
public class IndexService {

    private final LLMSegmenterService llmSegmenterService;
    private final InvertedIndexManager invertedIndexManager;
    private final TFIDFCalculator tfidfCalculator; // 注入TFIDFCalculator
    private final SimilarityCalculator similarityCalculator; // 注入SimilarityCalculator

    public IndexService(LLMSegmenterService llmSegmenterService,
                        InvertedIndexManager invertedIndexManager,
                        TFIDFCalculator tfidfCalculator, // 注入
                        SimilarityCalculator similarityCalculator) { // 注入
        this.llmSegmenterService = llmSegmenterService;
        this.invertedIndexManager = invertedIndexManager;
        this.tfidfCalculator = tfidfCalculator; // 赋值
        this.similarityCalculator = similarityCalculator; // 赋值
    }

    /**
     * 处理单个Markdown文档，进行分词并准备构建索引。
     * @param markdownFilePath 讲座Markdown文档路径
     * @return 分词后的词项列表
     */
    public ChatResponse processMarkdownDocumentForTerms(Path markdownFilePath) {
        String plainTextContent;
        String prompt = "请对以下中文文本进行分词和摘要提取。返回 JSON 格式，包含以下字段：\n" +
                "1. `title_text_tokenized`: 文本标题的分词结果，作为**JSON字符串数组**，例如 `[\"词1\", \"词2\"]`。\n" +
                "2. `full_text_tokenized`: 文本正文的完整分词结果，作为**JSON字符串数组**，例如 `[\"词1\", \"词2\"]`。\n" +
                "3. `speaker`: 该讲座的主讲人姓名字符串。\n" +
                "请确保严格按照 JSON 格式输出，如果缺失标题或正文请使用空数组 `[]` 代替，如果缺失主讲人请使用空字符串 `\"\"` 代替。分词时忽略标点符号。不要包含其他任何解释或说明，直接返回JSON。\n" +
                "文本内容：\n";
        try {
            plainTextContent = MarkdownProcessor.convertMarkdownToFullText(markdownFilePath);

            log.info("Successfully converted Markdown {} to plain text.", markdownFilePath.getFileName());
        } catch (IOException e) {
            log.error("Failed to read or convert Markdown file: {}", markdownFilePath, e);
            return null;
        }

        // 调用大模型API进行分词
        return llmSegmenterService.segmentTextWithLlm(prompt, plainTextContent);
    }

    /**
     * 批量处理所有文档以构建初始索引。
     *
     * @param documentPaths      所有Markdown文档的路径列表
     * @param forceRebuild
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
//            Set<String> allDocumentIds = new HashSet<>(currentInvertedIndex.getAllDocumentIds());
//            similarityCalculator.precomputeDocumentNorms(allDocumentIds);
            return;
        }

        // 清空旧索引，开始重建
        currentInvertedIndex.getDictionary().clear();
        currentInvertedIndex.getDocumentStore().clear();
        currentInvertedIndex.getDocumentFrequencies().clear();

        buildIndexForAllDoc(documentPaths, indexingThreadPool);

        Set<String> allDocumentIds = new HashSet<>(currentInvertedIndex.getAllDocumentIds());
        similarityCalculator.precomputeDocumentNorms(allDocumentIds);
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
                ChatResponse response = processMarkdownDocumentForTerms(path);

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

