package com.springbloom.domain.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.springbloom.domain.model.DailyUsage;
import com.springbloom.domain.model.TokenUsage;
import com.springbloom.domain.model.UsageTotals;

public interface AgentUsageRepository {

    void record(UUID conversationId, TokenUsage usage, boolean hadImage);

    /** Everything from the given instant on; null means all time. */
    UsageTotals totalsSince(Instant from);

    /** One row per store-local day that had turns, oldest first. */
    List<DailyUsage> dailySince(Instant from);
}
