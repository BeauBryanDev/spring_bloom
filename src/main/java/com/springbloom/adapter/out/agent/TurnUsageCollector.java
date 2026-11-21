package com.springbloom.adapter.out.agent;

import java.util.Optional;

import com.springbloom.domain.model.TokenUsage;

/**
 * Sums the usage of every model call made on this thread between begin() and
 * end(). A turn with tools is several HTTP calls, and Spring AI's own running
 * total drops the cache counts, so the exact figures are read per response.
 */
public final class TurnUsageCollector {

    private static final ThreadLocal<Totals> CURRENT = new ThreadLocal<>();

    private TurnUsageCollector() {
    }

    public static void begin() {
        CURRENT.set(new Totals());
    }

    /** A no-op outside a turn, so a call made elsewhere (an embedding, a test) is never counted. */
    public static void add(String model, 
                            int input, 
                            int output, 
                            int cacheRead, 
                            int cacheWrite) {

        Totals totals = CURRENT.get();
        if (totals != null) {
            totals.add(model, input, output, cacheRead, cacheWrite);
        }
    }

    public static Optional<TokenUsage> end() {
        Totals totals = CURRENT.get();
        CURRENT.remove();
        if (totals == null || totals.calls == 0) {
            return Optional.empty();
        }
        return Optional.of(new TokenUsage(
                totals.model == null || totals.model.isBlank() ? "unknown" : totals.model,
                totals.input, totals.output, totals.cacheRead, totals.cacheWrite));
    }

    private static final class Totals {
        private int calls;
        private String model;
        private int input;
        private int output;
        private int cacheRead;
        private int cacheWrite;

        void add(String modelName, int in, int out, int read, int write) {
            calls++;
            if (modelName != null && !modelName.isBlank()) {
                model = modelName;
            }
            input += in;
            output += out;
            cacheRead += read;
            cacheWrite += write;
        }
    }
}
