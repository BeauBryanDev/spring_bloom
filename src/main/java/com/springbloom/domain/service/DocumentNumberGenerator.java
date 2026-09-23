package com.springbloom.domain.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.random.RandomGenerator;

/**
 * Builds human readable document numbers: COT-20260825-48395 for quotations,
 * ORD-... for orders. The date scopes collisions to a single day; the UNIQUE
 * constraint on the column is what actually guarantees uniqueness, so callers
 * retry with a fresh number when an insert is rejected.
 */
public class DocumentNumberGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int SUFFIX_BOUND = 100_000;

    private final String prefix;
    private final Clock clock;
    private final RandomGenerator random;

    public DocumentNumberGenerator(String prefix, Clock clock, RandomGenerator random) {

        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix is required");
        }
        this.prefix = prefix;
        this.clock = clock;
        this.random = random;
    }

    /** The clock's zone decides the date, so it must be the store's zone, not the server's. */
    public String next() {
        
        String date = LocalDate.now(clock).format(DATE);
        return "%s-%s-%05d".formatted(prefix, date, random.nextInt(SUFFIX_BOUND));
    }
}
