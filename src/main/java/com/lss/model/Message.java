package com.lss.model;

import lombok.Data;

// 内部类定义请求/响应结构
@Data
public class Message {
    private String role;
    private String content;

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
    }
}

