package com.springbloom.domain.port.in;

import java.math.BigDecimal;
import java.util.List;

import com.springbloom.domain.model.DailyUsage;
import com.springbloom.domain.model.UsageTotals;

public interface AgentUsageQueryUseCase {

    Summary summarize();

    record Period(String label, UsageTotals totals, BigDecimal estimatedUsd) {
    }

    record Summary(List<Period> periods, List<DailyUsage> daily, long peakDayTokens) {
    }
}
