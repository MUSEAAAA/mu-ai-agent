package com.muse.muaiagent.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Uses process memory when no persistent ChatMemory implementation is enabled.
 */
@Configuration
public class ChatMemoryConfig {

    @Bean
    @ConditionalOnMissingBean(ChatMemory.class)
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }
}
