package com.springbloom.domain.service.pricing;

import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.vo.Money;

import java.math.BigDecimal;

/** Bundles are discounted; the schema requires a non-null discount for BOUQUET. */
public class BouquetPricingStrategy implements PricingStrategy {

    @Override
    public ProductType supportedType() {
        return ProductType.BOUQUET;
    }

    @Override
    public Money calculatePrice(Money composedSubtotal, BigDecimal discountPercentage) {
        if (discountPercentage == null) {
            throw new IllegalArgumentException("BOUQUET items require a discount percentage");
        }
        return composedSubtotal.applyDiscount(discountPercentage);
    }
}
