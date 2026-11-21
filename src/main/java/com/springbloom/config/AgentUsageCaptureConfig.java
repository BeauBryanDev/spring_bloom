package com.springbloom.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.springbloom.adapter.out.agent.AnthropicUsageInterceptor;

/** The interceptor only acts on Anthropic messages calls, so adding it to every RestClient is harmless. */
@Configuration
public class AgentUsageCaptureConfig {

    @Bean
    public RestClientCustomizer anthropicUsageCustomizer() {
        return builder -> builder.requestInterceptor(new AnthropicUsageInterceptor());
    }
}
