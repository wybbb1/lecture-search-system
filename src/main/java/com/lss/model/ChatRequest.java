package com.lss.model;

import lombok.Data;

import java.util.List;

/**
 * 请求体的数据内部结构，用于构建请求 JSON
 */
@Data
public class ChatRequest {
    private String model = "deepseek-chat";
    private List<Message> messages;
    private double temperature = 1;
    private int max_tokens = 8192;

    public ChatRequest(List<Message> messages) {
        this.messages = messages;
    }

}
