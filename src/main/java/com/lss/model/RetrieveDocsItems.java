package com.lss.model;

import com.lss.model.Index.LectureDocument;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrieveDocsItems {
    private LectureDocument document; // 匹配到的文档对象
    private double score;            // 相关性分数 (余弦相似度)
    // 可以添加更多用于呈现的字段，例如：
    // private String highlightedSnippet; // 突出显示摘要
}