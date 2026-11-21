package com.springbloom.domain.model;

/** What one agent turn cost in tokens, summed over every model round trip in it. */
public record TokenUsage(
        String model,
        int inputTokens,
        int outputTokens,
        int cacheReadTokens,
        int cacheWriteTokens) {

    public TokenUsage {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model is required");
        }
        if (inputTokens < 0 || outputTokens < 0 || cacheReadTokens < 0 || cacheWriteTokens < 0) {
            throw new IllegalArgumentException("token counts cannot be negative");
        }
    }

    public int total() {
        return inputTokens + outputTokens;
    }
}
