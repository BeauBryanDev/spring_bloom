package com.springbloom.config;

import com.springbloom.domain.service.pricing.BouquetPricingStrategy;
import com.springbloom.domain.service.pricing.GarlandPricingStrategy;
import com.springbloom.domain.service.pricing.IndividualPricingStrategy;
import com.springbloom.domain.service.pricing.PricingStrategy;
import com.springbloom.domain.service.pricing.PricingStrategyFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Wires the annotation-free pricing domain classes into beans. */
@Configuration
public class PricingConfig {

    @Bean
    public PricingStrategy individualPricingStrategy() {
        return new IndividualPricingStrategy();
    }

    @Bean
    public PricingStrategy bouquetPricingStrategy() {
        return new BouquetPricingStrategy();
    }

    @Bean
    public PricingStrategy garlandPricingStrategy() {
        return new GarlandPricingStrategy();
    }

    @Bean
    public PricingStrategyFactory pricingStrategyFactory(List<PricingStrategy> strategies) {
        return new PricingStrategyFactory(strategies);
    }
}
