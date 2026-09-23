package com.springbloom.domain.service.pricing;

import java.math.BigDecimal;

/**
 * How much a bouquet is discounted, decided from the number of stems in it.
 *
 * This exists because the discount used to be an argument every caller invented
 * for itself. That was survivable while a human chose it, and became a real
 * defect the moment the agent was a caller: the model was asked to supply a
 * commercial term, and the only honest thing it could do was ask the customer
 * what discount they would like. A quotation is a price the shop offers, not one
 * the customer or the model names.
 *
 * Static and stateless like SpeciesSearch: no bean, no Spring, and its test runs
 * with no context.
 */
public final class BouquetDiscountPolicy {

    /**
     * The tiers, ascending. The 15-stem boundary is not arbitrary: every catalog
     * card prices a reference "Bouquet (15)" and a customer can see that figure
     * before asking for a quotation, so 15 stems must discount at 15% or the
     * storefront and the document would contradict each other.
     */
    private static final int[] MIN_STEMS = {60, 30, 15, 10};
    private static final String[] PERCENTAGES = {"25.00", "20.00", "15.00", "10.00"};

    private static final BigDecimal NO_DISCOUNT = new BigDecimal("0.00");

    private BouquetDiscountPolicy() {
    }

    /**
     * The discount for a bouquet of this many stems. Never null: the schema
     * requires a non-null percentage on a BOUQUET line, and a small bouquet is
     * discounted at zero rather than at nothing.
     */
    public static BigDecimal discountFor(int totalStems) {
        if (totalStems <= 0) {
            throw new IllegalArgumentException("a bouquet needs at least one stem: " + totalStems);
        }
        for (int i = 0; i < MIN_STEMS.length; i++) {
            if (totalStems >= MIN_STEMS[i]) {
                return new BigDecimal(PERCENTAGES[i]);
            }
        }
        return NO_DISCOUNT;
    }
}
