package com.lss.model.ChatDoc;

import com.google.gson.annotations.SerializedName; // Gson 的注解
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List; // 用于List<String>

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatDocResponse {

    @SerializedName("title_text_tokenized")
    private List<String> titleTextTokenized; // 对应 JSON 中的字符串数组

    @SerializedName("full_text_tokenized")
    private List<String> fullTextTokenized; // 对应 JSON 中的字符串数组

    @SerializedName("speaker")
    private String speaker;

    @Override
    public String toString() {
        return "ChatDocResponse{" +
               "titleTextTokenized=" + titleTextTokenized +
               ", fullTextTokenized=" + fullTextTokenized +
               ", speaker='" + speaker + '\'' +
               '}';
    }
}