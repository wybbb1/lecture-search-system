package com.lss.model.Chat;

import com.google.gson.annotations.SerializedName; // Gson 的注解
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List; // 用于List<String>

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    @SerializedName("title_text_tokenized")
    private List<String> titleTextTokenized; // 对应 JSON 中的字符串数组

    @SerializedName("full_text_tokenized")
    private List<String> fullTextTokenized; // 对应 JSON 中的字符串数组

    @SerializedName("speaker")
    private String speaker;

    @SerializedName("query_text_tokenized")
    private List<String> queryTextTokenized; // 对应 JSON 中的字符串数组，用于查询分词

    @SerializedName("correct_query")
    private String correctQuery; // 对应 JSON 中的字符串，表示纠正后的查询文本
}