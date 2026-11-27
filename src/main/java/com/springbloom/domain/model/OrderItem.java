package com.springbloom.domain.model;

import java.math.BigDecimal;
import java.util.List;

import com.springbloom.domain.model.vo.Money;

/**
 * One order line, copied from its quotation line. Either species rows (a flower,
 * bouquet or composed garland) or a fixed-price arrangement, never both.
 */
public record OrderItem(
        ProductType productType,
        BigDecimal discountPercentage,
        List<OrderItemSpecies> species,
        Money subtotal,
        CreationLine creation) {

    public OrderItem {
        if (productType == null) {
            throw new IllegalArgumentException("productType is required");
        }
        if (subtotal == null) {
            throw new IllegalArgumentException("subtotal is required");
        }
        species = species == null ? List.of() : List.copyOf(species);

        if (creation != null) {
            requireArrangement(productType, discountPercentage, species);
        } else {
            requireComposed(productType, discountPercentage, species);
        }
    }

    private static void requireArrangement(ProductType productType, 
                                            BigDecimal discountPercentage,
                                            List<OrderItemSpecies> species) {
                                                
        if (!productType.arrangement() || !species.isEmpty()) {
            throw new IllegalArgumentException("An arrangement line is a GARLAND or CROWN with no species");
        }
        if (discountPercentage == null || discountPercentage.signum() != 0) {
            throw new IllegalArgumentException("Arrangements are sold without discount");
        }
    }

    private static void requireComposed(ProductType productType, BigDecimal discountPercentage,
            List<OrderItemSpecies> species) {
        if (!productType.pricedFromStems()) {
            throw new IllegalArgumentException(productType + " lines are sold only as an arrangement");
        }
        if (species.isEmpty()) {
            throw new IllegalArgumentException("An order line needs at least one species");
        }
        if (productType == ProductType.INDIVIDUAL && discountPercentage != null) {
            throw new IllegalArgumentException("INDIVIDUAL lines cannot carry a discount");
        }
        if (productType != ProductType.INDIVIDUAL && discountPercentage == null) {
            throw new IllegalArgumentException(productType + " lines require a discount percentage");
        }
    }

    public OrderItem(ProductType productType, 
                    BigDecimal discountPercentage,
                    List<OrderItemSpecies> species, 
                    Money subtotal) {

        this(productType, discountPercentage, species, subtotal, null);
    }

    public boolean isArrangement() {
        return creation != null;
    }

    static OrderItem from(QuotationItem item) {
        return new OrderItem(
                item.productType(),
                item.discountPercentage(),
                item.species().stream().map(OrderItemSpecies::from).toList(),
                item.subtotal(),
                item.creation());
    }
}
