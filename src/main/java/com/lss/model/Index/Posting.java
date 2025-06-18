// src/main/java/com/yourcompany/lecturesystem/model/Posting.java
package com.lss.model.Index;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Posting implements Serializable {
    private static final long serialVersionUID = 302L;

    private String documentId; // 包含该词项的文档ID
    private int termFrequency; // 词项在该文档中出现的频率 (TF)
    private List<Integer> positions; // 词项在该文档中出现的所有位置 (用于邻近搜索)
                                   // 列表为空则表示不存储位置信息

    public Posting(String documentId) {
        this.documentId = documentId;
        this.termFrequency = 0;
        this.positions = new ArrayList<>();;
    }

    // 增加一个词项出现位置的方法
    public void addPosition(int position) {
        this.positions.add(position);
        this.termFrequency++; // 每添加一个位置，词频加1
    }
}