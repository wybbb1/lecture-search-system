package com.lss.service;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

@AiService(
        chatModel = "openAiChatModel",
        wiringMode = EXPLICIT
)
public interface QueryAdviceAssistant {
    @SystemMessage(fromResource = "QueryAISystemPrompt.txt")
    String chat(@UserMessage String query);
}
