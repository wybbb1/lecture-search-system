// src/main/java/com/lss/service/SearchService.java
package com.lss.service;

import com.lss.model.Chat.ChatResponse;
import com.lss.model.Index.LectureDocument;
import com.lss.model.Index.LectureDocumentVO;
import com.lss.model.Index.Posting;
import com.lss.model.Result;
import com.lss.model.RetrieveDocsItems;
import com.lss.repository.InvertedIndexManager;
import com.lss.repository.MarkdownManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

        // 1. 对查询字符串进行分词 (使用LLM服务，它现在返回Mono<ChatDocResponse>)
        String prompt = "请对以下中文文本进行分词和摘要提取。返回 JSON 格式，包含以下字段：\n" +
                "1. `query_text_tokenized`: 用户查询的分词结果，作为**JSON字符串数组**，例如 `[\"词1\", \"词2\"]`。\n" +
                "2. `correct_query`: 正确的查询意图，作为**字符串**。如果用户查询内容不符合常理（例如打错字），请为该字段附上可能的值，否则请使用空字符串 `\"\"` 代替。\n" +
                "请确保严格按照 JSON 格式输出，如果缺失用户查询请使用空数组 `[]` 代替。分词时忽略标点符号。不要包含其他任何解释或说明，直接返回JSON。\n";

        // TODO：可以使用分词依赖来代替大模型，大模型只需要处理纠错
        ChatResponse chatResponse = llmSegmenterService.segmentTextWithLlm(prompt, queryString);

        if (chatResponse != null && chatResponse.getQueryTextTokenized() != null) {
            List<String> queryTerms = chatResponse.getQueryTextTokenized();

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
                        return new LectureDocumentVO(doc.getId(), doc.getTitle().split("\\.")[0]);
                    })
                    .collect(Collectors.toList());

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
}