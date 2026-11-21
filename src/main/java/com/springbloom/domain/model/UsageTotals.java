package com.springbloom.domain.model;

/** Token usage summed over a period. A period with no turns is all zeros, never null. */
public record UsageTotals(
        long turns,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens) {

    public static final UsageTotals NONE = new UsageTotals(0, 0, 0, 0, 0);

    public long totalTokens() {
        return inputTokens + outputTokens;
    }

    public long averageTokensPerTurn() {
        return turns == 0 ? 0 : totalTokens() / turns;
    }
}
