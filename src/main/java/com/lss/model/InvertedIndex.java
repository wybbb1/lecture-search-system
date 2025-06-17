// src/main/java/com/yourcompany/lecturesystem/model/InvertedIndex.java
package com.lss.model;

import lombok.Data;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Data
public class InvertedIndex implements Serializable {
    private static final long serialVersionUID = 1L;

    // 词典：term -> List<Posting>
    // 使用ConcurrentHashMap以支持潜在的并发构建或更新
    private final Map<String, List<Posting>> dictionary;

    // 存储文档元数据，方便检索后获取文档详情
    // documentId -> LectureDocument
    private final Map<String, LectureDocument> documentStore;

    // 用于计算IDF的文档频率 (Document Frequency)
    // term -> count_of_documents_containing_term
    private final Map<String, Integer> documentFrequencies;

    public InvertedIndex() {
        this.dictionary = new ConcurrentHashMap<>();
        this.documentStore = new ConcurrentHashMap<>();
        this.documentFrequencies = new ConcurrentHashMap<>();
    }

    /**
     * 添加一个文档到索引中。
     * @param document 文档对象
     * @param termsInDocument 文档分词后的词项列表，顺序表示位置
     * @param fieldType 词项所属的域（例如：TITLE, SUMMARY, SPEAKER），用于按域检索
     */
    public void addDocument(LectureDocument document, List<String> termsInDocument, String fieldType) {
        String docId = document.getId();
        documentStore.putIfAbsent(docId, document);

        // 临时Set用于记录当前文档中已出现的原始词项，以正确统计DF
        Set<String> uniqueTermsInThisDocument = new HashSet<>(); // 导入 java.util.HashSet

        Map<String, List<Integer>> termPositionsInDoc = new HashMap<>();
        for (int i = 0; i < termsInDocument.size(); i++) {
            String term = termsInDocument.get(i);
            termPositionsInDoc.computeIfAbsent(term, k -> new ArrayList<>()).add(i);
            uniqueTermsInThisDocument.add(term); // 记录原始词项
        }

        // 更新倒排记录表 (这部分保持不变)
        for (Map.Entry<String, List<Integer>> entry : termPositionsInDoc.entrySet()) {
            String term = entry.getKey();
            List<Integer> positions = entry.getValue();
            String indexedTerm = fieldType + ":" + term;
            dictionary.computeIfAbsent(indexedTerm, k -> new LinkedList<>())
                    .add(new Posting(docId, positions.size(), positions));
        }

        // 仅对当前文档中所有出现过的“原始词项”（不带域前缀）更新一次DF
        // 确保 DF 统计的是包含该词项的文档数量，而不是该词项在文档中不同域出现的次数
        for (String uniqueTerm : uniqueTermsInThisDocument) {
            documentFrequencies.merge(uniqueTerm, 1, Integer::sum);
        }
    }

    /**
     * 获取某个词项的倒排记录表。
     * @param term 词项
     * @return 倒排记录表列表，如果不存在则返回空列表
     */
    public List<Posting> getPostings(String term) {
        return dictionary.getOrDefault(term, Collections.emptyList());
    }

    /**
     * 获取包含某个词项的文档数量 (用于IDF)。
     * @param term 词项
     * @return 包含该词项的文档数量
     */
    public int getDocumentFrequency(String term) {
        return documentFrequencies.getOrDefault(term, 0);
    }

    /**
     * 获取文档总数。
     * @return 文档总数
     */
    public int getTotalDocuments() {
        return documentStore.size();
    }

    /**
     * 根据文档ID获取文档元数据。
     * @param docId 文档ID
     * @return LectureDocument对象
     */
    public LectureDocument getDocument(String docId) {
        return documentStore.get(docId);
    }

    /**
     * 获取所有文档的ID列表，用于后续遍历或TF-IDF计算。
     * @return 所有文档ID
     */
    public List<String> getAllDocumentIds() {
        return new ArrayList<>(documentStore.keySet());
    }
}