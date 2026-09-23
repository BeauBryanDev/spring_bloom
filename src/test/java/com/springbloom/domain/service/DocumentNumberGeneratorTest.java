package com.springbloom.domain.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A fixed clock makes the date deterministic, so the format can be asserted exactly. */
class DocumentNumberGeneratorTest {

    private static final Clock FIXED = Clock.fixed(
            Instant.parse("2026-08-25T15:30:00Z"), ZoneId.of("America/Bogota"));

    @Test
    @DisplayName("the number is prefix, date and five padded digits")
    void formatsAsSpecified() {
        DocumentNumberGenerator generator =
                new DocumentNumberGenerator("COT", FIXED, fixedRandom(48395));

        assertThat(generator.next()).isEqualTo("COT-20260825-48395");
    }

    @Test
    @DisplayName("small numbers are zero padded to keep every number the same width")
    void padsShortSuffixes() {
        DocumentNumberGenerator generator =
                new DocumentNumberGenerator("COT", FIXED, fixedRandom(7));

        assertThat(generator.next()).isEqualTo("COT-20260825-00007");
    }

    @Test
    @DisplayName("orders use the same scheme with their own prefix")
    void supportsOrderPrefix() {
        DocumentNumberGenerator generator =
                new DocumentNumberGenerator("ORD", FIXED, fixedRandom(12345));

        assertThat(generator.next()).isEqualTo("ORD-20260825-12345");
    }

    @Test
    @DisplayName("the number always fits the VARCHAR(40) column")
    void fitsTheColumn() {
        DocumentNumberGenerator generator =
                new DocumentNumberGenerator("COT", FIXED, RandomGenerator.getDefault());

        assertThat(generator.next()).hasSize(18).hasSizeLessThanOrEqualTo(40);
    }

    @Test
    @DisplayName("the store zone decides the date, not the server zone")
    void usesTheStoreZone() {
        Instant lateEvening = Instant.parse("2026-08-26T03:00:00Z");

        DocumentNumberGenerator bogota = new DocumentNumberGenerator(
                "COT", Clock.fixed(lateEvening, ZoneId.of("America/Bogota")), fixedRandom(1));
        DocumentNumberGenerator utc = new DocumentNumberGenerator(
                "COT", Clock.fixed(lateEvening, ZoneId.of("UTC")), fixedRandom(1));

        assertThat(bogota.next()).isEqualTo("COT-20260825-00001");
        assertThat(utc.next())
                .as("the same instant is already the next day in UTC")
                .isEqualTo("COT-20260826-00001");
    }

    @Test
    @DisplayName("suffixes stay inside five digits across many draws")
    void neverExceedsFiveDigits() {
        DocumentNumberGenerator generator =
                new DocumentNumberGenerator("COT", FIXED, RandomGenerator.getDefault());

        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            String number = generator.next();
            assertThat(number).matches("COT-\\d{8}-\\d{5}");
            generated.add(number);
        }

        assertThat(generated)
                .as("duplicates are expected at this volume, which is why the column is UNIQUE")
                .hasSizeLessThan(20_000);
    }

    private static RandomGenerator fixedRandom(int value) {
        return new RandomGenerator() {
            @Override
            public int nextInt(int bound) {
                return value;
            }

            @Override
            public long nextLong() {
                return value;
            }
        };
    }
}
