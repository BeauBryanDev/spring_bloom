package com.springbloom.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.api.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.api.AnthropicCacheTtl;
import org.springframework.ai.chat.messages.MessageType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAIConfigTest {

    @Test
    @DisplayName("strategy and ttl strings map to the Spring AI enums, case-insensitively")
    void maps() {
        var options = SpringAIConfig.cacheOptions("conversation_history", " five_minutes ");

        assertThat(options.getStrategy()).isEqualTo(AnthropicCacheStrategy.CONVERSATION_HISTORY);
        assertThat(options.getMessageTypeTtl().get(MessageType.SYSTEM)).isEqualTo(AnthropicCacheTtl.FIVE_MINUTES);
        assertThat(options.getMessageTypeTtl().get(MessageType.TOOL)).isEqualTo(AnthropicCacheTtl.FIVE_MINUTES);
    }

    @Test
    @DisplayName("a typo fails loudly instead of quietly disabling caching")
    void typo() {
        assertThatThrownBy(() -> SpringAIConfig.cacheOptions("CONVERSATION", "FIVE_MINUTES"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpringAIConfig.cacheOptions("SYSTEM_ONLY", "2H"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
