package com.lss.model.Index;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LectureDocumentVO implements Serializable {
    private static final long serialVersionUID = 301L; // 用于序列化

    private String id; // 文档唯一ID，可以是文件名哈希，或递增ID
    private String title; // 讲座题目
    private String content; // 文档内容

    public LectureDocumentVO(String id, String title) {
        this.id = id;
        this.title = title;
        this.content = ""; // 默认内容为空
    }

}