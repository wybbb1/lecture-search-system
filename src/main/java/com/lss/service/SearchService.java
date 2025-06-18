// src/main/java/com/lss/service/SearchService.java
package com.lss.service;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.lss.constant.PathConstant;
import com.lss.model.Chat.ChatResponse;
import com.lss.model.Index.LectureDocument;
import com.lss.model.Index.LectureDocumentVO;
import com.lss.model.Index.Posting;
import com.lss.model.Result;
import com.lss.model.RetrieveDocsItems;
import com.lss.repository.InvertedIndexManager;
import com.lss.repository.MarkdownManager;
import com.lss.util.MarkdownProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SearchService {

    @Value("${llm.api.url}")
    private String llmApiUrl;

    @Value("${llm.api.key}")
    private String llmApiKey;

    private final LLMSegmenterService llmSegmenterService; // 用于查询分词
    private final SimilarityCalculator similarityCalculator;
    private final InvertedIndexManager invertedIndexManager;
    private final MarkdownManager markdownManager;

    public SearchService(LLMSegmenterService llmSegmenterService,
                         SimilarityCalculator similarityCalculator,
                         InvertedIndexManager invertedIndexManager,
                         MarkdownManager markdownManager) {
        this.llmSegmenterService = llmSegmenterService;
        this.similarityCalculator = similarityCalculator;
        this.invertedIndexManager = invertedIndexManager;
        this.markdownManager = markdownManager;
    }

    /**
     * 执行信息检索。
     *
     * @param queryString 用户输入的查询字符串
     * @param topN 返回结果的数量，例如10
     * @return 包含搜索结果和耗时的SearchResult对象
     */
    public Result search(String queryString, int topN) {
        long startTime = System.nanoTime(); // 记录开始时间

        if (queryString == null || queryString.trim().isEmpty()) {
            return Result.fail("请输入查询内容");
        }
        // TODO:纠错服务
        // 1. 对查询字符串进行分词 (使用jieba分词器)
        JiebaSegmenter segmenter = new JiebaSegmenter();
        List<String> queryTerms = segmenter.sentenceProcess(queryString);

        if (queryTerms != null && !queryTerms.isEmpty()) {
            // 2. 倒排索引查找匹配文档 (布尔检索部分)
            // 获取包含任何一个查询词项的文档ID集合
            Map<String, List<String>> queryTermsByField = splitQueryTermsByField(queryTerms); // 可以扩展为按域查询

            Set<String> candidateDocIds = new HashSet<>();
            for (Map.Entry<String, List<String>> entry : queryTermsByField.entrySet()) {
                String fieldPrefix = entry.getKey(); // 例如 "BODY:"
                List<String> termsInField = entry.getValue();
                for (String term : termsInField) {
                    String indexedTerm = fieldPrefix + ":" + term; // 构造带域的索引词项
                    List<Posting> postings = invertedIndexManager.getPostingsList(indexedTerm);
                    postings.forEach(posting -> candidateDocIds.add(posting.getDocumentId()));
                }
            }

            // 如果没有匹配文档
            if (candidateDocIds.isEmpty()) {
                long endTime = System.nanoTime();
                long durationMillis = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
                log.info("查询耗时" + durationMillis + "ms");
                return Result.ok(Collections.emptyList());
            }

            // 3. 计算相关性分数 (余弦相似度)
            List<RetrieveDocsItems> results = new ArrayList<>();
            for (String docId : candidateDocIds) {
                double similarityScore = similarityCalculator.calculateCosineSimilarity(queryTerms, docId);
                if (similarityScore > 0) { // 只添加相似度大于0的文档
                    LectureDocument doc = invertedIndexManager.getDocumentById(docId);
                    if (doc != null) {
                        results.add(new RetrieveDocsItems(doc, similarityScore));
                    }
                }
            }

            // 4. 排序：按相似度降序排列
            results.sort(Comparator.comparingDouble(RetrieveDocsItems::getScore).reversed());

            // 5. 返回 Top N 结果
            List<RetrieveDocsItems> topResults = results.stream()
                    .limit(topN)
                    .collect(Collectors.toList());

            long endTime = System.nanoTime();
            long durationMillis = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
            log.info("查询耗时" + durationMillis + "ms");

            log.info("查询结果数量: " + topResults.size());

            List<LectureDocumentVO> topDocs = topResults.stream()
                    .map(item -> {
                        LectureDocument doc = item.getDocument();
                        try {
                            return new LectureDocumentVO(doc.getId(), doc.getTitle().split("\\.")[0], markdownManager.getContentByPath(Path.of(doc.getOriginalFilePath())));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());

            // 将doc文本写入到Prompt
            // 这里可以将查询结果的文档内容写入到Prompt中，供后续使用
            try (InputStream IS = new FileInputStream(PathConstant.TopN_Content);
            ){
                if (IS.available() > 0) {
                    // 清空文件内容
                    new PrintWriter(new FileOutputStream(PathConstant.TopN_Content, false)).close();
                }
                // 将查询结果写入到文件
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(PathConstant.TopN_Content))) {
                    for (LectureDocumentVO doc : topDocs) {
                        writer.write("ID: " + doc.getId() + "\n");
                        writer.write("Title: " + doc.getTitle() + "\n");
                        writer.write("Content:\n" + doc.getContent() + "\n\n");
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return Result.ok(topDocs);
        }else {
            // 如果分词结果为空，返回错误
            return Result.fail("请输入查询内容");
        }
    }

    // TODO:按域检索功能未开发
    // 辅助方法：将查询词项映射到不同的域
    // 简化：这里假设所有查询词项都匹配"BODY"域，或者未来可以解析查询语法来指定域
    private Map<String, List<String>> splitQueryTermsByField(List<String> queryTerms) {
        Map<String, List<String>> fieldTerms = new HashMap<>();
        // 默认将查询词项视为在BODY域中搜索
        fieldTerms.put("FullText", queryTerms);
        // 如果需要支持按域查询，例如 "title:战略" "speaker:李琛"，需要更复杂的查询解析器
        // 或者简单地在所有主要文本域中搜索
//         fieldTerms.put("Title", queryTerms);
        // fieldTerms.put("SPEAKER", queryTerms);
        // fieldTerms.put("ORGANIZER", queryTerms);
        return fieldTerms;
    }

    public Result searchById(String id) {
        LectureDocument doc = invertedIndexManager.getDocumentById(id);
        if (doc == null) {
            return Result.fail("未找到指定ID的文档");
        }

        // 返回文档信息和内容

        String Content = null;
        try {
            Content = markdownManager.getContentByPath(Path.of(doc.getOriginalFilePath()));
        } catch (IOException e) {
            throw new RuntimeException("获取文档内容失败: " + e.getMessage());
        }
        LectureDocumentVO documentVO = new LectureDocumentVO(doc.getId(), doc.getTitle().split("\\.")[0], Content);

        return Result.ok(documentVO);
    }
}