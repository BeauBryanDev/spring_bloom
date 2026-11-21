package com.springbloom.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springbloom.domain.model.DailyUsage;
import com.springbloom.domain.model.TokenPricing;
import com.springbloom.domain.model.UsageTotals;
import com.springbloom.domain.port.in.AgentUsageQueryUseCase;
import com.springbloom.domain.port.out.AgentUsageRepository;

import lombok.RequiredArgsConstructor;

/** Days are store-local (the storeClock zone), not the server's. */
@Service
@RequiredArgsConstructor
public class AgentUsageQueryService implements AgentUsageQueryUseCase {

    static final int DAILY_WINDOW_DAYS = 30;

    private final AgentUsageRepository usage;
    private final TokenPricing pricing;
    private final Clock storeClock;

    @Override
    @Transactional(readOnly = true)
    public Summary summarize() {
        LocalDate today = LocalDate.now(storeClock);

        List<Period> periods = new ArrayList<>();
        periods.add(period("Hoy", usage.totalsSince(startOf(today))));
        periods.add(period("Ultimos 7 dias", usage.totalsSince(startOf(today.minusDays(6)))));
        periods.add(period("Ultimos 30 dias", usage.totalsSince(startOf(today.minusDays(DAILY_WINDOW_DAYS - 1)))));
        periods.add(period("Total", usage.totalsSince(null)));

        List<DailyUsage> daily = usage.dailySince(startOf(today.minusDays(DAILY_WINDOW_DAYS - 1))).stream()
                .sorted(Comparator.comparing(DailyUsage::day).reversed())
                .toList();
        long peak = daily.stream().mapToLong(d -> d.totals().totalTokens()).max().orElse(0);

        return new Summary(periods, daily, peak);
    }

    private Period period(String label, UsageTotals totals) {
        return new Period(label, totals, pricing.estimate(totals));
    }

    private Instant startOf(LocalDate day) {
        return day.atStartOfDay(storeClock.getZone()).toInstant();
    }
}
