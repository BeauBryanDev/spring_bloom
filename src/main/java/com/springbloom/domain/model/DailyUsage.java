package com.springbloom.domain.model;

import java.time.LocalDate;

/** One store-local day of agent usage. */
public record DailyUsage(LocalDate day, UsageTotals totals) {
}
