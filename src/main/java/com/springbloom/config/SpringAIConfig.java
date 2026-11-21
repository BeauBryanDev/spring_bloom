package com.springbloom.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.api.AnthropicCacheTtl;
import org.springframework.ai.chat.messages.MessageType;
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
    public ChatClient florabelleChatClient(
            AnthropicChatModel chatModel,
            @Value("${spring.ai.anthropic.chat.options.model}") String model,
            @Value("${florabelle.agent.cache.strategy}") String strategy,
            @Value("${florabelle.agent.cache.ttl}") String ttl) {

        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(model)
                        .cacheOptions(cacheOptions(strategy, ttl))
                        .build())
                .build();
    }

    /** An unknown strategy or TTL fails the boot: a typo must not quietly turn caching off. */
    static AnthropicCacheOptions cacheOptions(String strategy, String ttl) {
        AnthropicCacheStrategy chosen = AnthropicCacheStrategy.valueOf(strategy.trim().toUpperCase());
        AnthropicCacheTtl lifetime = AnthropicCacheTtl.valueOf(ttl.trim().toUpperCase());
        return AnthropicCacheOptions.builder()
                .strategy(chosen)
                .messageTypeTtl(MessageType.SYSTEM, lifetime)
                .messageTypeTtl(MessageType.USER, lifetime)
                .messageTypeTtl(MessageType.ASSISTANT, lifetime)
                .messageTypeTtl(MessageType.TOOL, lifetime)
                .build();
    }
}
