package com.springbloom.config;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.springbloom.domain.model.TokenPricing;

/** Rates live in application.yml so a price change never touches a class. */
@Configuration
public class AgentPricingConfig {

    @Bean
    public TokenPricing tokenPricing(
            @Value("${florabelle.agent.pricing.input-per-million}") BigDecimal input,
            @Value("${florabelle.agent.pricing.output-per-million}") BigDecimal output,
            @Value("${florabelle.agent.pricing.cache-read-per-million}") BigDecimal cacheRead,
            @Value("${florabelle.agent.pricing.cache-write-per-million}") BigDecimal cacheWrite) {
                
        return new TokenPricing(input, output, cacheRead, cacheWrite);
    }
}
