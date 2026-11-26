package com.springbloom.domain.service.pricing;

import java.math.BigDecimal;

import com.springbloom.domain.model.ProductType;

/**
 * How much a bouquet is discounted, decided from the number of stems in it.
 *
 * This exists because the discount used to be an argument every caller invented
 * for itself.
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

    /**
     * The line type for a basket of this many kinds and stems. One kind below the
     * first tier is sold by the stem; anything else is a bouquet, so the volume
     * discount follows the stem count whichever way the customer asked for it.
     */
    public static ProductType lineTypeFor(int kinds, int totalStems) {
        if (kinds <= 0) {
            throw new IllegalArgumentException("a line needs at least one kind of flower: " + kinds);
        }
        boolean discounted = discountFor(totalStems).signum() > 0;
        return kinds == 1 && !discounted ? ProductType.INDIVIDUAL : ProductType.BOUQUET;
    }
}
