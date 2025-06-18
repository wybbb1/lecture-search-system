package com.lss.model.Index;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LectureDocument implements Serializable {
    private static final long serialVersionUID = 301L; // 用于序列化

    private String id; // 文档唯一ID，可以是文件名哈希，或递增ID
    private String title; // 讲座题目
    private String content;
    private String abstractText;
    private String originalFilePath; // 原始Markdown文件路径
    private double vectorNorm; // 新增字段：存储文档向量的欧几里得范数

    @Override
    public String toString() {
        return "LectureDocument{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", abstractText='" + abstractText + '\'' +
                ", originalFilePath='" + originalFilePath + '\'' +
                ", vectorNorm=" + vectorNorm +
                '}';
    }

}