package com.springbloom.domain.model;

import com.springbloom.domain.model.vo.Money;

/**
 * One species inside a quotation line, with the snapshots the schema requires.
 * The name and unit price are copied at quote time on purpose: a quotation must
 * still read the same after the catalog or the price list moves.
 */
public record QuotationItemSpecies(
        Long speciesId,
        String commonNameSnapshot,
        int quantity,
        Money unitPriceSnapshot,
        Money lineTotal) {

    public QuotationItemSpecies {

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

    /**
     * Snapshots the live effective price, so the import multiplier is baked in
     * and cannot be forgotten later.
     */
    public static QuotationItemSpecies of(FlowerSpecies species, 
        FlowerStock stock, 
        int quantity) {

        if (!species.getId().equals(stock.getSpeciesId())) {

            throw new IllegalArgumentException(
                    "Stock does not belong to species " + species.getSpeciesKey());
        }
        Money unitPrice = stock.effectiveUnitPrice();

        return new QuotationItemSpecies(

                species.getId(),
                species.getCommonName(),
                quantity,
                unitPrice,
                unitPrice.multiply(quantity)
            );
    }
}
