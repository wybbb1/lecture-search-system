package com.lss.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate; // 用于讲座日期，如果数据中包含

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LectureDocument implements Serializable {
    private static final long serialVersionUID = 1L; // 用于序列化

    private String id; // 文档唯一ID，可以是文件名哈希，或递增ID
    private String title; // 讲座题目
    private String content;
    private String abstractText;
    private String originalFilePath; // 原始Markdown文件路径

}