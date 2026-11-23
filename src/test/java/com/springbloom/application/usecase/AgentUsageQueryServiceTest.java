package com.springbloom.application.usecase;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.springbloom.domain.model.DailyUsage;
import com.springbloom.domain.model.TokenPricing;
import com.springbloom.domain.model.UsageTotals;
import com.springbloom.domain.port.in.AgentUsageQueryUseCase.Summary;
import com.springbloom.domain.port.out.AgentUsageRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentUsageQueryServiceTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    /** 22:00 in Bogota on 26 Aug is already 27 Aug in UTC. */
    private static final Instant NOW = Instant.parse("2026-08-27T03:00:00Z");

    private AgentUsageRepository usage;
    private AgentUsageQueryService service;

    @BeforeEach
    void setUp() {
        usage = mock(AgentUsageRepository.class);
        when(usage.totalsSince(any())).thenReturn(UsageTotals.NONE);
        when(usage.dailySince(any())).thenReturn(List.of());
        TokenPricing pricing = new TokenPricing(
                new BigDecimal("1.00"), new BigDecimal("5.00"), new BigDecimal("0.10"), new BigDecimal("1.25"));
        service = new AgentUsageQueryService(usage, mock(com.springbloom.domain.port.out.GuardrailEventRepository.class), pricing, Clock.fixed(NOW, BOGOTA));
    }

    @Test
    @DisplayName("an empty history gives four zero periods, not nulls")
    void empty() {
        Summary summary = service.summarize();

        assertThat(summary.periods()).extracting(p -> p.label())
                .containsExactly("Hoy", "Ultimos 7 dias", "Ultimos 30 dias", "Total");
        assertThat(summary.periods()).allSatisfy(p -> {
            assertThat(p.totals()).isEqualTo(UsageTotals.NONE);
            assertThat(p.estimatedUsd()).isEqualByComparingTo("0");
        });
        assertThat(summary.daily()).isEmpty();
        assertThat(summary.peakDayTokens()).isZero();
    }

    @Test
    @DisplayName("today starts at midnight in Bogota, not at midnight UTC")
    void todayIsStoreLocal() {
        service.summarize();

        verify(usage).totalsSince(Instant.parse("2026-08-26T05:00:00Z"));
        verify(usage).totalsSince(isNull());
    }

    @Test
    @DisplayName("cost comes from the configured rates, and the daily series is newest first with a peak")
    void costAndDaily() {
        when(usage.totalsSince(isNull())).thenReturn(new UsageTotals(4, 1_000_000, 200_000, 0, 0));
        when(usage.dailySince(any())).thenReturn(List.of(
                new DailyUsage(LocalDate.parse("2026-08-25"), new UsageTotals(1, 100, 50, 0, 0)),
                new DailyUsage(LocalDate.parse("2026-08-26"), new UsageTotals(2, 400, 100, 0, 0))));

        Summary summary = service.summarize();

        assertThat(summary.periods().get(3).estimatedUsd()).isEqualByComparingTo("2.0000");
        assertThat(summary.daily()).extracting(DailyUsage::day)
                .containsExactly(LocalDate.parse("2026-08-26"), LocalDate.parse("2026-08-25"));
        assertThat(summary.peakDayTokens()).isEqualTo(500);
    }
}
