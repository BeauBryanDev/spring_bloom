package com.springbloom.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * The agent's persona lives in a resource file, not in a Java string.
 */
@Configuration
public class SpringAIConfig {

    @Value("classpath:prompts/florabelle-system.txt")
    private Resource systemPrompt;

    /**
     * The Anthropic model is asked for by type rather than as a plain
     * ChatModel: OpenAI is on the classpath for embeddings, and injecting the
     * interface would silently depend on its chat autoconfiguration staying
     * disabled.
     */
    @Bean
    public ChatClient florabelleChatClient(AnthropicChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .build();
    }
}
