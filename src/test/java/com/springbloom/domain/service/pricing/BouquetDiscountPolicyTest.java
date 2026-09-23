package com.springbloom.domain.service.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure: no Spring, no database. */
class BouquetDiscountPolicyTest {

    @ParameterizedTest(name = "{0} stems -> {1}%")
    @CsvSource({
            "1,  0.00",
            "9,  0.00",
            "10, 10.00",
            "14, 10.00",
            "15, 15.00",
            "29, 15.00",
            "30, 20.00",
            "59, 20.00",
            "60, 25.00",
            "500, 25.00"
    })
    @DisplayName("the tiers apply at their boundaries")
    void tiers(int stems, String expected) {
        assertThat(BouquetDiscountPolicy.discountFor(stems))
                .isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    @DisplayName("15 stems discount at 15%, which is what every catalog card advertises")
    void fifteenStemsMatchTheCatalogCard() {
        assertThat(BouquetDiscountPolicy.discountFor(15))
                .isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    @DisplayName("a small bouquet is discounted at zero, never at null")
    void smallBouquetIsZeroNotNull() {
        assertThat(BouquetDiscountPolicy.discountFor(3))
                .isNotNull()
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("the scale is always two decimals, matching NUMERIC(5,2)")
    void scaleMatchesTheColumn() {
        assertThat(BouquetDiscountPolicy.discountFor(15).scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("a bouquet of no stems is a bug, not a free bouquet")
    void nonPositiveRejected() {
        assertThatThrownBy(() -> BouquetDiscountPolicy.discountFor(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BouquetDiscountPolicy.discountFor(-5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
