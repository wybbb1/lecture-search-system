package com.lss.service;

import com.lss.model.Index.InvertedIndex;
import com.lss.model.Index.LectureDocument;
import com.lss.model.Index.Posting;
import com.lss.repository.InvertedIndexManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class SimilarityCalculator {

    private final TFIDFCalculator tfidfCalculator;
    private final InvertedIndexManager invertedIndexManager;

    public SimilarityCalculator(TFIDFCalculator tfidfCalculator, InvertedIndexManager invertedIndexManager) {
        this.tfidfCalculator = tfidfCalculator;
        this.invertedIndexManager = invertedIndexManager;
    }

    /**
     * 预计算所有文档的向量范数，并存储在LectureDocument对象中。
     * 这个方法应该在索引构建完成后，在IndexService中被调用。
     *
     * @param documentsToProcess 待处理的文档ID列表
     */
    public void precomputeDocumentNorms(Set<String> documentsToProcess) {
        log.info("Starting precomputation of document vector norms for {} documents.", documentsToProcess.size());
        InvertedIndex invertedIndex = invertedIndexManager.getInvertedIndex();

        for (String docId : documentsToProcess) {
            LectureDocument doc = invertedIndex.getDocument(docId);
            if (doc == null) {
                log.warn("Document with ID '{}' not found during norm precomputation. Skipping.", docId);
                continue;
            }

            double sumOfSquares = 0.0;
            // 获取该文档包含的所有词项及其频率
            // 这需要遍历所有Posting，找到对应文档的TF。
            // 这种方式效率较低，更好的方式是在构建索引时，同时累加平方和。
            // 让我们在InvertedIndex中添加一个获取文档所有词项TF的方法
            // 或者更简单：遍历倒排索引的dictionary，找到所有包含docId的Posting
            // 考虑到InvertedIndex的结构，直接迭代所有词项来获取其在特定文档的TF
            for (Map.Entry<String, List<Posting>> entry : invertedIndex.getDictionary().entrySet()) {
                String indexedTerm = entry.getKey(); // "FIELD:term"
                String originalTerm = indexedTerm.contains(":") ? indexedTerm.substring(indexedTerm.indexOf(":") + 1) : indexedTerm;

                List<Posting> postings = entry.getValue();
                for (Posting posting : postings) {
                    if (posting.getDocumentId().equals(docId)) {
                        double tf = tfidfCalculator.calculateTF(posting.getTermFrequency());
                        double idf = tfidfCalculator.calculateIDF(originalTerm); // IDF计算需要原始词项
                        double tfidf = tf * idf;
                        sumOfSquares += tfidf * tfidf;
                    }
                }
            }
            doc.setVectorNorm(Math.sqrt(sumOfSquares));
            log.debug("Precomputed norm for document '{}' ({}): {}", doc.getTitle(), docId, doc.getVectorNorm());
        }
        log.info("Completed precomputation of document vector norms.");
    }


    /**
     * 计算查询向量的范数。
     * @param queryVector 查询词项的TF-IDF权重映射 (term -> tf-idf_weight)
     * @return 查询向量的欧几里得范数
     */
    private double calculateQueryNorm(Map<String, Double> queryVector) {
        double sumOfSquares = 0.0;
        for (double weight : queryVector.values()) {
            sumOfSquares += weight * weight;
        }
        return Math.sqrt(sumOfSquares);
    }

    /**
     * 计算查询与单个文档的余弦相似度。
     *
     * @param queryTerms 查询分词后的词项列表
     * @param documentId 待比较的文档ID
     * @return 余弦相似度得分，范围[0, 1]
     */
    public double calculateCosineSimilarity(List<String> queryTerms, String documentId) {
        if (queryTerms == null || queryTerms.isEmpty()) {
            return 0.0;
        }

        InvertedIndex invertedIndex = invertedIndexManager.getInvertedIndex();
        LectureDocument document = invertedIndex.getDocument(documentId);

        if (document == null || document.getVectorNorm() == 0.0) {
            // 文档不存在或范数为零 (例如，文档为空或所有词项TF-IDF为0)
            return 0.0;
        }

        // 1. 构建查询向量 (TF-IDF权重)
        Map<String, Integer> queryTermFrequencies = new HashMap<>();
        for (String term : queryTerms) {
            queryTermFrequencies.merge(term, 1, Integer::sum);
        }

        Map<String, Double> queryVector = new HashMap<>();
        for (Map.Entry<String, Integer> entry : queryTermFrequencies.entrySet()) {
            String term = entry.getKey();
            int tfInQuery = entry.getValue();
            // 在查询中，TF-IDF的IDF部分也应基于整个文档集合计算
            double tfidf = tfidfCalculator.calculateTFIDF(tfInQuery, term);
            queryVector.put(term, tfidf);
        }

        // 2. 计算查询范数
        double queryNorm = calculateQueryNorm(queryVector);
        if (queryNorm == 0.0) {
            return 0.0; // 查询范数为零，无法计算相似度 (例如，查询全是停用词)
        }

        // 3. 计算点积 (Dot Product)
        // 对于查询中的每个词项 q_t
        // 找到该词项在文档D中所有域的TF-IDF权重之和（或者平均值）
        // 假设queryTerm是原始词项，需要找到它在文档中的所有域权重
        double sumProduct = 0.0;
        for (Map.Entry<String, Double> queryEntry : queryVector.entrySet()) {
            String queryTerm = queryEntry.getKey(); // 查询词项，不带域
            double queryTermWeight = queryEntry.getValue();

            // 聚合文档中该词项在所有相关域的TF-IDF权重
            double aggregatedDocTermWeight = 0.0;
            // 遍历所有可能的域前缀 (例如 "TITLE:", "BODY:", "SPEAKER:", "ORGANIZER:")
            List<String> possibleFields = List.of("TITLE", "FullText", "SPEAKER");

            for (String field : possibleFields) {
                String indexedTermInDoc = field + ":" + queryTerm;
                List<Posting> postings = invertedIndex.getPostings(indexedTermInDoc);
                for (Posting posting : postings) {
                    if (posting.getDocumentId().equals(documentId)) {
                        double tf = tfidfCalculator.calculateTF(posting.getTermFrequency());
                        double idf = tfidfCalculator.calculateIDF(queryTerm); // IDF是针对原始词项
                        aggregatedDocTermWeight += tf * idf; // 累加该词项在不同域的权重
                        // 注意：如果一个词项在文档的多个域中出现，这里的累加可能会导致权重过高
                        // 实际中可能需要更复杂的聚合策略（例如求和后归一化，或只取最高值）
                        break; // 找到该域中第一个匹配的即可
                    }
                }
            }
            sumProduct += queryTermWeight * aggregatedDocTermWeight;
        }

        // 4. 计算余弦相似度
        return sumProduct / (queryNorm * document.getVectorNorm());
    }
}