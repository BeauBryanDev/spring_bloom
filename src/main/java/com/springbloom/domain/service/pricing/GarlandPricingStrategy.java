package com.springbloom.domain.service.pricing;

import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.vo.Money;

import java.math.BigDecimal;

/**
 * Same discount rule as BOUQUET today, kept as its own strategy because
 * garlands are where assembly/labour surcharges will land.
 */
public class GarlandPricingStrategy implements PricingStrategy {

    @Override
    public ProductType supportedType() {
        return ProductType.GARLAND;
    }

    @Override
    public Money calculatePrice(Money composedSubtotal, BigDecimal discountPercentage) {
        if (discountPercentage == null) {
            throw new IllegalArgumentException("GARLAND items require a discount percentage");
        }
        return composedSubtotal.applyDiscount(discountPercentage);
    }
}
