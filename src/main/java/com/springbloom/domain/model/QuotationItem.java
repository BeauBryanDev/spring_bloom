package com.springbloom.domain.model;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.springbloom.domain.model.vo.Money;

/**
 * One quotation line: an INDIVIDUAL flower, a BOUQUET/GARLAND composed from
 * several species, or a fixed-price arrangement (creation set, no species).
 * composedSubtotal is what the line adds up to before discount; subtotal is what
 * the customer pays.
 */
public record QuotationItem(
        ProductType productType,
        BigDecimal discountPercentage,
        List<QuotationItemSpecies> species,
        Money composedSubtotal,
        Money subtotal,
        CreationLine creation) {

    public static final BigDecimal NO_DISCOUNT = new BigDecimal("0.00");

    public QuotationItem {

        if (productType == null) {
            throw new IllegalArgumentException("productType is required");
        }
        species = species == null ? List.of() : List.copyOf(species);

        if (creation != null) {
            requireArrangement(productType, discountPercentage, 
                                species, composedSubtotal, 
                                subtotal, creation);
        } else {
            requireComposed(productType, discountPercentage, species);
        }
    }

    public QuotationItem(ProductType productType, 
                        BigDecimal discountPercentage,
                        List<QuotationItemSpecies> species, 
                        Money composedSubtotal, 
                        Money subtotal) {

        this(productType, discountPercentage, species, composedSubtotal, subtotal, null);
    }

    /** A fixed-price garland or crown: no volume discount, no stems. */
    public static QuotationItem of(Creation creation, int units) {

        CreationLine line = CreationLine.of(creation, units);

        return new QuotationItem(creation.productType(), NO_DISCOUNT, List.of(),
                line.total(), line.total(), line);
    }

    public boolean isArrangement() {
        return creation != null;
    }

    /** Named differently from the component: a record accessor must return the component's type. */
    public Optional<BigDecimal> discount() {
        return Optional.ofNullable(discountPercentage);
    }

    /** What the discount actually saved, for display and for quotation.discount_amount. */
    public Money discountAmount() {
        return composedSubtotal.minus(subtotal);
    }

    /** Arrangements have no stem count, so they add nothing here. */
    public int totalStems() {
        return species.stream().mapToInt(QuotationItemSpecies::quantity).sum();
    }

    private static void requireComposed(ProductType productType, 
                                        BigDecimal discountPercentage,
                                        List<QuotationItemSpecies> species) {

        if (!productType.pricedFromStems()) {
            throw new IllegalArgumentException(productType + " lines are sold only as an arrangement");
        }
        if (species.isEmpty()) {
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
    }

    private static void requireArrangement(ProductType productType, 
                                            BigDecimal discountPercentage,
                                            List<QuotationItemSpecies> species, 
                                            Money composedSubtotal, 
                                            Money subtotal,
                                            CreationLine creation) {

        if (!productType.arrangement()) {
            throw new IllegalArgumentException(productType + " lines cannot be an arrangement");
        }
        if (!species.isEmpty()) {
            throw new IllegalArgumentException("An arrangement line carries no species rows");
        }
        if (discountPercentage == null || discountPercentage.signum() != 0) {
            throw new IllegalArgumentException("Arrangements are sold at their fixed price, without discount");
        }
        if (!creation.total().equals(subtotal) || !subtotal.equals(composedSubtotal)) {
            throw new IllegalArgumentException("An arrangement line costs its price times its units");
        }
    }
}
