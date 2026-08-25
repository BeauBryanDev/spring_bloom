package com.springbloom.domain.service.pricing;

import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.vo.Money;

import java.math.BigDecimal;

/**
 * Single flowers are sold at list price. The schema enforces the same rule:
 * ck_quotation_item_discount_only_bundle requires discount_percentage to be
 * NULL for INDIVIDUAL, so a non-null discount here is a programming error.
 */
public class IndividualPricingStrategy implements PricingStrategy {

    @Override
    public ProductType supportedType() {
        return ProductType.INDIVIDUAL;
    }

    @Override
    public Money calculatePrice(Money composedSubtotal, BigDecimal discountPercentage) {
        if (discountPercentage != null) {
            throw new IllegalArgumentException("INDIVIDUAL items cannot carry a discount");
        }
        return composedSubtotal;
    }
}
