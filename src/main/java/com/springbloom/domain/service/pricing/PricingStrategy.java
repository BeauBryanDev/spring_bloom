package com.springbloom.domain.service.pricing;

import java.math.BigDecimal;

import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.vo.Money;

/**
 * How the price of one quotation/order line is computed, per ProductType.

 */
public interface PricingStrategy {

    /** The product type this strategy handles; used by PricingStrategyFactory. */
    ProductType supportedType();

    /**
     * @param composedSubtotal   sum of the line's species totals, never null
     * @param discountPercentage 0..100, or null when no discount applies
     */
    Money calculatePrice(Money composedSubtotal, BigDecimal discountPercentage);
}
