package com.springbloom.domain.model;

import java.math.BigDecimal;
import java.util.List;

import com.springbloom.domain.model.vo.Money;

/**
 * One order line. The INDIVIDUAL-without / BOUQUET-with discount rule is applied
 * here even though the order_item table lacks the check constraint that
 * quotation_item has: the asymmetry is a V1 oversight, not a licence.
 */
public record OrderItem(
        ProductType productType,
        BigDecimal discountPercentage,
        List<OrderItemSpecies> species,
        Money subtotal) {

    public OrderItem {
        if (productType == null) {
            throw new IllegalArgumentException("productType is required");
        }
        if (species == null || species.isEmpty()) {
            throw new IllegalArgumentException("An order line needs at least one species");
        }
        if (subtotal == null) {
            throw new IllegalArgumentException("subtotal is required");
        }
        if (productType == ProductType.INDIVIDUAL && discountPercentage != null) {
            throw new IllegalArgumentException("INDIVIDUAL lines cannot carry a discount");
        }
        if (productType != ProductType.INDIVIDUAL && discountPercentage == null) {
            throw new IllegalArgumentException(productType + " lines require a discount percentage");
        }
        species = List.copyOf(species);
    }

    static OrderItem from(QuotationItem item) {
        return new OrderItem(
                item.productType(),
                item.discountPercentage(),
                item.species().stream().map(OrderItemSpecies::from).toList(),
                item.subtotal());
    }
}
