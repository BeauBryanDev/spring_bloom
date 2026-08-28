package com.springbloom.domain.model.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable monetary amount, always scaled to 2 decimals with HALF_UP rounding
 * to match the NUMERIC(10,2) columns in the schema.
 *
 * A record gives us equals/hashCode/toString by value, which is exactly the
 * semantics a value object wants: two Money of 6.00 are the same money.
 */
public record Money(BigDecimal amount) {

    public static final Money ZERO = Money.of(BigDecimal.ZERO);

    public Money {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Money cannot be negative: " + amount);
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public Money plus(Money other) {
        return Money.of(this.amount.add(other.amount));
    }

    /** Never negative: subtracting more than the amount is a programming error. */
    public Money minus(Money other) {

        BigDecimal result = this.amount.subtract(other.amount);

        if (result.signum() < 0) {
            // This is a programming error, not a user error.
            throw new IllegalArgumentException(
                    "Cannot subtract " + other.amount + " from " + this.amount);
        }
        return Money.of(result);
    }

    public Money multiply(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity cannot be negative: " + quantity);
        }
        return Money.of(this.amount.multiply(BigDecimal.valueOf(quantity)));
    }

    /** Multiplies by a factor such as flower_stock.import_price_multiplier. */
    public Money multiply(BigDecimal factor) {
        return Money.of(this.amount.multiply(factor));
    }

    /** @param percentage 0..100, e.g. 15 removes 15% from the amount. */
    public Money applyDiscount(BigDecimal percentage) {

        if (percentage.signum() < 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            
            throw new IllegalArgumentException("discount must be within 0..100: " + percentage);
        }
        BigDecimal keptFraction = BigDecimal.ONE
                .subtract(percentage.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
                
        return Money.of(this.amount.multiply(keptFraction));
    }
}
