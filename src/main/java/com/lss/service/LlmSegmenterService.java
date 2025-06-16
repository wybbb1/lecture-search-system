package com.lss.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.lss.model.ChatRequest;
import com.lss.model.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class LlmSegmenterService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper; // 用于JSON处理

    // 从application.properties或application.yml中读取API配置
    @Value("${llm.api.url}")
    private String llmApiUrl;

    @Value("${llm.api.key}")
    private String llmApiKey;

    public LlmSegmenterService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * 调用大模型API进行文本分词。
     * 这是一个通用示例，您需要根据实际大模型API的文档来调整请求和响应结构。
     *
     * @param text 待分词的文本
     * @return 分词结果列表
     */
    public List<String> segmentTextWithLlm(String text) {
        // 1. 构建请求体 (根据大模型API文档定义)
        Gson gson = new Gson();
        ChatRequest chatRequest = new ChatRequest(List.of(
                new Message("user", "请将以下中文文本进行分词，并只返回分词结果，每个词用空格隔开，不要包含其他任何解释或说明：\n" + text)
        ));
        String requestBody = gson.toJson(chatRequest);

        // 2. 发送HTTP请求
        Mono<String> responseMono = webClient.post()
                .uri(llmApiUrl)
                .header("Authorization", "Bearer " + llmApiKey) // 如果需要Bearer Token认证
                .header("Content-Type", "application/json")
                .body(BodyInserters.fromValue(requestBody))
                .retrieve()
                .bodyToMono(String.class); // 获取原始JSON字符串
        log.info("Calling LLM API : {}", responseMono);

        // 3. 解析响应
        String rawResponse = responseMono.block(); // 阻塞等待响应
        log.debug("LLM API raw response: {}", rawResponse);

        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            // 假设大模型返回的JSON结构中，分词结果在 "choices[0].message.content" 或 "result" 字段
            JsonNode contentNode = null;
            if (root.has("choices") && root.get("choices").isArray() && root.get("choices").get(0).has("message")) {
                    contentNode = root.get("choices").get(0).get("message").get("content");
            }

            if (contentNode != null) {
                String segmentedText = contentNode.asText();
                // 假设大模型返回的是用空格分隔的词语字符串
                return List.of(segmentedText.split("\\s+"));
            }

            return Collections.emptyList();

        } catch (Exception e) {
            log.error("Failed to parse LLM API response: {}", rawResponse, e);
            return Collections.emptyList();
        }
    }
}