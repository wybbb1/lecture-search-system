package com.lss.service;

import com.lss.repository.InvertedIndexManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class TFIDFCalculator {

    private final InvertedIndexManager invertedIndexManager;

    // 可以选择缓存IDF值，避免重复计算，尤其是在IDF值稳定后
    // 词项 -> IDF值
    private final Map<String, Double> idfCache = new ConcurrentHashMap<>();

    public TFIDFCalculator(InvertedIndexManager invertedIndexManager) {
        this.invertedIndexManager = invertedIndexManager;
    }

    /**
     * 计算词项在文档中的词频 (TF)。
     * 在我们的Posting对象中已经存储了该信息，这里只是一个获取器。
     * 实际TF可以是原始计数、频率、对数频率等。这里使用原始计数。
     * @param termFrequencyInDocument 词项在文档中的原始频率
     * @return 词项频率 (TF)
     */
    public double calculateTF(int termFrequencyInDocument) {
        // 最简单的TF计算就是原始频率。
        // 其他常见的TF变种：
        // 1. log-frequency weighting: 1 + log(termFrequencyInDocument)
        // 2. boolean frequency: 1 (if termFrequencyInDocument > 0), 0 (otherwise)
        // 3. augmented frequency: 0.5 + (0.5 * termFrequencyInDocument / maxFreqInDoc)
        return (double) termFrequencyInDocument;
    }

    /**
     * 计算词项的逆文档频率 (IDF)。
     * 采用平滑处理：ln(N / DF(t) + 1)
     * @param term 词项
     * @return 逆文档频率 (IDF)
     */
    public double calculateIDF(String term) {
        // 尝试从缓存获取IDF值
        return idfCache.computeIfAbsent(term, k -> {
            int N = invertedIndexManager.getTotalDocumentsCount(); // 文档总数
            int df = invertedIndexManager.getDocumentFrequency(k); // 包含该词项的文档数

            // 避免除以零：如果df为0，则设置一个非常小的正数或直接返回0，
            // 实际上，如果df为0，该词项不会出现在任何文档中，IDF就没有意义。
            // 考虑到我们公式是 N/DF + 1，如果DF为0，直接N/0+1会报错。
            // 这里的 df 应该至少为 1 (如果词项存在于至少一个文档中)
            if (df == 0) {
                // 如果词项的df为0，意味着它不在任何文档中，其IDF应为0或接近0，从而TF-IDF也为0
                // 此时通常意味着这是一个不应出现在有效查询中的词，或者索引有误。
                log.warn("Term '{}' has a document frequency of 0. Returning IDF as 0.0.", term);
                return 0.0;
            }

            // 使用自然对数 ln
            double idf = Math.log((double) N / df + 1); // 平滑处理：ln(N / DF(t) + 1)
            log.debug("Calculated IDF for '{}': N={}, DF={}, IDF={}", term, N, df, idf);
            return idf;
        });
    }

    /**
     * 计算词项的TF-IDF权重。
     * @param termFrequencyInDocument 词项在文档中的原始频率
     * @param term 词项
     * @return TF-IDF权重
     */
    public double calculateTFIDF(int termFrequencyInDocument, String term) {
        double tf = calculateTF(termFrequencyInDocument);
        double idf = calculateIDF(term);
        return tf * idf;
    }
}