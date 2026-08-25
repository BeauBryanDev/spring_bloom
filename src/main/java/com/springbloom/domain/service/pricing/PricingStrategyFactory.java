package com.springbloom.domain.service.pricing;

import com.springbloom.domain.model.ProductType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the strategy for a ProductType. Adding a new product type means
 * adding a strategy and registering it in the Spring config - no switch here
 * and none at the call sites.
 */
public class PricingStrategyFactory {

    private final Map<ProductType, PricingStrategy> strategies = new EnumMap<>(ProductType.class);

    public PricingStrategyFactory(List<PricingStrategy> strategies) {
        for (PricingStrategy strategy : strategies) {
            PricingStrategy previous = this.strategies.put(strategy.supportedType(), strategy);
            if (previous != null) {
                throw new IllegalStateException("Two strategies for " + strategy.supportedType());
            }
        }
    }

    public PricingStrategy forType(ProductType type) {
        PricingStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new IllegalStateException("No pricing strategy registered for " + type);
        }
        return strategy;
    }
}
