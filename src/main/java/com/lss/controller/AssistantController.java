package com.lss.controller;

import com.lss.constant.PathConstant;
import com.lss.service.AiAssistant;
import com.lss.model.ChatForm;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@RestController
public class AssistantController {
    private static Set<String> memoryIds = new HashSet<>();

    @Resource
    private AiAssistant aiAssistant;
    
    @PostMapping(value = "/chat", produces = "text/stream;charset=UTF-8")
    public Flux<String> chat(@RequestBody ChatForm chatForm) {
        if (!memoryIds.contains(chatForm.getMemoryId())){
            memoryIds.add(chatForm.getMemoryId());
            Path filePath = Paths.get(PathConstant.TopN_Content);

            try {
                chatForm.setUserMessage(Files.readString(filePath) + "\n" + chatForm.getUserMessage());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            log.info(chatForm.getUserMessage());
        }

        return aiAssistant.chat(chatForm.getMemoryId(), chatForm.getUserMessage());
    }
    
    @GetMapping(value="/reset")
    public void reset(String memoryId) {
        aiAssistant.evictChatMemory(memoryId);
        // 用于重置会话
    }
}
