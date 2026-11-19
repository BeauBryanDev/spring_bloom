package com.springbloom.domain.model;

import com.springbloom.domain.model.vo.Money;

/**
 * One species inside an order line, with the snapshots copied from its
 * quotation.
 */
public record OrderItemSpecies(
        Long speciesId,
        String commonNameSnapshot,
        int quantity,
        Money unitPriceSnapshot,
        Money lineTotal) {

    public OrderItemSpecies {
        if (speciesId == null) {
            throw new IllegalArgumentException("speciesId is required");
        }
        if (commonNameSnapshot == null || commonNameSnapshot.isBlank()) {
            throw new IllegalArgumentException("commonNameSnapshot is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
        if (unitPriceSnapshot == null || lineTotal == null) {
            throw new IllegalArgumentException("prices are required");
        }
    }

    static OrderItemSpecies from(QuotationItemSpecies line) {

        return new OrderItemSpecies(line.speciesId(),
                line.commonNameSnapshot(),
                line.quantity(),
                line.unitPriceSnapshot(),
                line.lineTotal());
    }
}
