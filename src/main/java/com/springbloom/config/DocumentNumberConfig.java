package com.springbloom.config;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.springbloom.domain.service.DocumentNumberGenerator;

/**
 * Document numbers carry a date, so they are generated in the store's zone
 * rather than the server's: a quotation raised at 20:00 in Bogota must not be
 * stamped with tomorrow's date because the host runs UTC.
 */
@Configuration
public class DocumentNumberConfig {

    private static final ZoneId STORE_ZONE = ZoneId.of("America/Bogota");

    @Bean
    public Clock storeClock() {
        return Clock.system(STORE_ZONE);
    }

    @Bean
    public DocumentNumberGenerator quotationNumberGenerator(Clock storeClock) {
        return new DocumentNumberGenerator("COT", storeClock, new SecureRandom());
    }

    @Bean
    public DocumentNumberGenerator orderNumberGenerator(Clock storeClock) {
        return new DocumentNumberGenerator("ORD", storeClock, new SecureRandom());
    }

    /** REC for "reclamo": the number a customer reads back when chasing a claim. */
    @Bean
    public DocumentNumberGenerator complaintNumberGenerator(Clock storeClock) {
        return new DocumentNumberGenerator("REC", storeClock, new SecureRandom());
    }
}
