package com.springbloom.adapter.out.persistence.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.springbloom.domain.model.DailyUsage;
import com.springbloom.domain.model.TokenUsage;
import com.springbloom.domain.model.UsageTotals;
import com.springbloom.domain.port.out.AgentUsageRepository;

import static org.assertj.core.api.Assertions.assertThat;

/** Rows are tagged with a throwaway model name, so cleanup and assertions never touch real usage. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_USER", matches = ".+")
class AgentUsageRepositoryAdapterTest {

    private static final String MODEL = "test-model-" + UUID.randomUUID();
    private static final Instant FAR_PAST = Instant.parse("2001-01-01T00:00:00Z");

    @Autowired
    private AgentUsageRepository usage;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM agent_usage WHERE model = ?", MODEL);
    }

    @Test
    @DisplayName("a write stores every count and the image flag, read back through SQL")
    void writes() {
        usage.record(null, new TokenUsage(MODEL, 1200, 300, 50, 25), true);

        var row = jdbc.queryForMap(
                "SELECT input_tokens, output_tokens, cache_read_tokens, cache_write_tokens, had_image, created_at"
                        + " FROM agent_usage WHERE model = ?", MODEL);
        assertThat(row.get("input_tokens")).isEqualTo(1200);
        assertThat(row.get("output_tokens")).isEqualTo(300);
        assertThat(row.get("cache_read_tokens")).isEqualTo(50);
        assertThat(row.get("cache_write_tokens")).isEqualTo(25);
        assertThat(row.get("had_image")).isEqualTo(true);
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    @DisplayName("totals sum the rows from the given instant on")
    void totals() {
        UsageTotals before = usage.totalsSince(FAR_PAST);
        usage.record(null, new TokenUsage(MODEL, 100, 10, 0, 0), false);
        usage.record(null, new TokenUsage(MODEL, 200, 20, 5, 1), false);

        UsageTotals after = usage.totalsSince(FAR_PAST);

        assertThat(after.turns() - before.turns()).isEqualTo(2);
        assertThat(after.inputTokens() - before.inputTokens()).isEqualTo(300);
        assertThat(after.outputTokens() - before.outputTokens()).isEqualTo(30);
        assertThat(after.cacheReadTokens() - before.cacheReadTokens()).isEqualTo(5);
        assertThat(usage.totalsSince(Instant.now().plusSeconds(3600))).isEqualTo(UsageTotals.NONE);
        assertThat(usage.totalsSince(null).turns()).isGreaterThanOrEqualTo(after.turns());
    }

    @Test
    @DisplayName("days are Bogota days: 23:30 Bogota on the 10th is the 11th in UTC but still the 10th")
    void bogotaDayBoundary() {
        jdbc.update("INSERT INTO agent_usage (model, input_tokens, output_tokens, created_at)"
                + " VALUES (?, 10, 1, '2026-03-11T04:30:00Z')", MODEL);
        jdbc.update("INSERT INTO agent_usage (model, input_tokens, output_tokens, created_at)"
                + " VALUES (?, 20, 2, '2026-03-11T05:30:00Z')", MODEL);

        List<DailyUsage> days = usage.dailySince(Instant.parse("2026-03-01T00:00:00Z")).stream()
                .filter(d -> d.day().equals(LocalDate.parse("2026-03-10"))
                        || d.day().equals(LocalDate.parse("2026-03-11")))
                .toList();

        assertThat(days).extracting(DailyUsage::day)
                .contains(LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-11"));
    }
}
