package com.lss.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssistantConfig {

    // 不持久化的内存级别的聊天内存提供者
    @Bean(name="openAiStreamingMemoryProviderNoPersist")
    public ChatMemoryProvider openAiStreamingMemoryProviderNoPersist() {
        return memoryId -> MessageWindowChatMemory.withMaxMessages(50);
    }


}

