package com.lss.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.lss.model.ChatDoc.ChatDocResponse;
import com.lss.model.ChatDoc.ChatRequest;
import com.lss.model.ChatDoc.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.List;

@Service
@Slf4j
public class LLMSegmenterService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper; // 用于JSON处理

    // 从application.properties或application.yml中读取API配置
    @Value("${llm.api.url}")
    private String llmApiUrl;

    @Value("${llm.api.key}")
    private String llmApiKey;

    public LLMSegmenterService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
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
    public ChatDocResponse segmentTextWithLlm(String text) {
        // 1. 构建请求体 (根据大模型API文档定义)
        Gson gson = new GsonBuilder().create();

        String prompt = "请对以下中文文本进行分词和摘要提取。返回 JSON 格式，包含以下字段：\n" +
                "1. `title_text_tokenized`: 文本标题的分词结果，作为**JSON字符串数组**，例如 `[\"词1\", \"词2\"]`。\n" +
                "2. `full_text_tokenized`: 文本正文的完整分词结果，作为**JSON字符串数组**，例如 `[\"词1\", \"词2\"]`。\n" +
                "3. `speaker`: 该讲座的主讲人姓名字符串。\n" +
                "请确保严格按照 JSON 格式输出，如果缺失标题或正文请使用空数组 `[]` 代替，如果缺失主讲人请使用空字符串 `\"\"` 代替。分词时忽略标点符号。不要包含其他任何解释或说明，直接返回JSON。\n" +
                "文本内容：\n" + text;

        ChatRequest chatRequest = new ChatRequest(List.of(
                new Message("user", prompt)
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

        String jsonString = null;
        ChatDocResponse response = null;
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            // 假设大模型返回的JSON结构中，分词结果在 "choices[0].message.content" 或 "result" 字段
            JsonNode contentNode = null;
            if (root.has("choices") && root.get("choices").isArray() && root.get("choices").get(0).has("message")) {
                contentNode = root.get("choices").get(0).get("message").get("content");
            }


            if (contentNode != null) {
                String segmentedText = contentNode.asText();
                jsonString = segmentedText.replace("```json\n", "").replace("```", "").trim();
                response = gson.fromJson(jsonString, ChatDocResponse.class);
                return response;
            }

        } catch (Exception e) {
            System.err.println("处理 AI 响应时发生未知错误：");
            e.printStackTrace();
        }
        return null;
    }
}