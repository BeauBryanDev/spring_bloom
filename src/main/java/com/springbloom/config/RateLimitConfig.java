package com.springbloom.config;

import java.time.Clock;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.springbloom.adapter.in.web.RateLimitFilter;
import com.springbloom.domain.port.out.GuardrailEventRepository;

/**
 * The filter is registered here rather than scanned, so a web slice test of one
 * controller does not have to supply its collaborators.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            RateLimitProperties properties,
            GuardrailEventRepository events,
            Clock storeClock) {

        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(
                new RateLimitFilter(properties, events, storeClock));
        registration.addUrlPatterns("/api/chat/*", RateLimitFilter.CHECKOUT_PATH);
        return registration;
    }
}
