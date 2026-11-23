package com.springbloom.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** USD per million tokens. The rates are configuration, so the estimate is only as current as they are. */
public record TokenPricing(
        BigDecimal inputPerMillion,
        BigDecimal outputPerMillion,
        BigDecimal cacheReadPerMillion,
        BigDecimal cacheWritePerMillion) {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    public BigDecimal estimate(UsageTotals usage) {
        
        return inputPerMillion.multiply(BigDecimal.valueOf(usage.inputTokens()))
                .add(outputPerMillion.multiply(BigDecimal.valueOf(usage.outputTokens())))
                .add(cacheReadPerMillion.multiply(BigDecimal.valueOf(usage.cacheReadTokens())))
                .add(cacheWritePerMillion.multiply(BigDecimal.valueOf(usage.cacheWriteTokens())))
                .divide(MILLION, 4, RoundingMode.HALF_UP);
    }
}
