package com.springbloom.domain.model;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenUsageTest {

    @Test
    @DisplayName("total is input plus output, and negatives or a blank model are refused")
    void shape() {
        assertThat(new TokenUsage("m", 1000, 250, 0, 0).total()).isEqualTo(1250);
        assertThatThrownBy(() -> new TokenUsage("m", -1, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenUsage(" ", 1, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the estimate applies each rate per million tokens")
    void estimate() {
        TokenPricing pricing = new TokenPricing(
                new BigDecimal("1.00"), new BigDecimal("5.00"), new BigDecimal("0.10"), new BigDecimal("1.25"));

        BigDecimal cost = pricing.estimate(new UsageTotals(3, 2_000_000, 1_000_000, 1_000_000, 400_000));

        assertThat(cost).isEqualByComparingTo("7.6000");
        assertThat(pricing.estimate(UsageTotals.NONE)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("an empty period averages to zero, not a division error")
    void emptyAverage() {
        assertThat(UsageTotals.NONE.averageTokensPerTurn()).isZero();
        assertThat(new UsageTotals(2, 100, 50, 0, 0).averageTokensPerTurn()).isEqualTo(75);
    }

    @Test
    @DisplayName("input served from cache is counted in the hit rate and the total input")
    void cacheShare() {
        UsageTotals totals = new UsageTotals(2, 100, 50, 900, 0);

        assertThat(totals.totalInputTokens()).isEqualTo(1000);
        assertThat(totals.cacheHitPercent()).isEqualTo(90);
        assertThat(totals.totalTokens()).isEqualTo(1050);
        assertThat(UsageTotals.NONE.cacheHitPercent()).isZero();
    }
}
