package com.springbloom.domain.model;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.springbloom.domain.model.vo.Money;

/**
 * One quotation line: an INDIVIDUAL flower, or a BOUQUET/GARLAND composed from
 * several species. composedSubtotal is what the species add up to; subtotal is
 * what the customer pays once the pricing strategy has run.
 *
 * The discount rule mirrors ck_quotation_item_discount_only_bundle. The order
 * side is missing that constraint in V1, so it is enforced here for both.
 */
public record QuotationItem(
        ProductType productType,
        BigDecimal discountPercentage,
        List<QuotationItemSpecies> species,
        Money composedSubtotal,
        Money subtotal ) {

    public QuotationItem {

        if (productType == null) {
            throw new IllegalArgumentException("productType is required");
        }
        if (species == null || species.isEmpty()) {
            throw new IllegalArgumentException("A quotation line needs at least one species");
        }

        if (productType == ProductType.INDIVIDUAL) {

            if (discountPercentage != null) {
                throw new IllegalArgumentException("INDIVIDUAL lines cannot carry a discount");
            }
            if (species.size() != 1) {
                throw new IllegalArgumentException(
                        "An INDIVIDUAL line holds exactly one species, not " + species.size());
            }
        } else if (discountPercentage == null) {
            throw new IllegalArgumentException(productType + " lines require a discount percentage");
        }

        Set<Long> seen = new HashSet<>();

        for (QuotationItemSpecies line : species) {

            if (!seen.add(line.speciesId())) {

                throw new IllegalArgumentException(
                        "Species " + line.speciesId() + " appears twice in one line");
            }
        }

        species = List.copyOf(species);
    }

    /** Named differently from the component: a record accessor must return the component's type. */
    public Optional<BigDecimal> discount() {
        return Optional.ofNullable(discountPercentage);
    }

    /** What the discount actually saved, for display and for quotation.discount_amount. */
    public Money discountAmount() {
        return composedSubtotal.minus(subtotal);
    }

    public int totalStems() {
        return species.stream().mapToInt(QuotationItemSpecies::quantity).sum();
    }
}
