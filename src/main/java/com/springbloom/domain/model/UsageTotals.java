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
        return totalInputTokens() + outputTokens;
    }

    /** Everything the model read: uncached input plus cache reads and writes. */
    public long totalInputTokens() {
        return inputTokens + cacheReadTokens + cacheWriteTokens;
    }

    /** Share of input served from cache, 0 to 100. */
    public int cacheHitPercent() {
        long total = totalInputTokens();
        return total == 0 ? 0 : (int) (cacheReadTokens * 100 / total);
    }

    public long averageTokensPerTurn() {
        return turns == 0 ? 0 : totalTokens() / turns;
    }
}
