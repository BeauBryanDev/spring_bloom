package com.springbloom.adapter.out.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.springbloom.domain.model.GuardrailEvent;
import com.springbloom.domain.model.GuardrailEvent.Stage;
import com.springbloom.domain.port.out.GuardrailEventRepository;

/** Rows carry a throwaway rule name, so cleanup and assertions never touch real events. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_USER", matches = ".+")
class GuardrailEventRepositoryAdapterTest {

    private static final String RULE = "test_rule_" + UUID.randomUUID().toString().substring(0, 8);
    private static final Instant FAR_PAST = Instant.parse("2001-01-01T00:00:00Z");

    @Autowired
    private GuardrailEventRepository events;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM guardrail_event WHERE rule = ?", RULE);
    }

    @Test
    @DisplayName("a write stores stage and rule, read back through SQL")
    void writes() {
        events.record(null, Stage.OUTPUT, RULE);

        var row = jdbc.queryForMap("SELECT stage, created_at FROM guardrail_event WHERE rule = ?", RULE);
        assertThat(row.get("stage")).isEqualTo("OUTPUT");
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    @DisplayName("the summary groups by stage and rule and counts")
    void summarises() {
        events.record(null, Stage.INPUT, RULE);
        events.record(null, Stage.INPUT, RULE);
        events.record(null, Stage.TOOL, RULE);

        var summary = events.summarySince(FAR_PAST).stream()
                .filter(e -> e.rule().equals(RULE))
                .toList();

        assertThat(summary).extracting(GuardrailEvent::stage, GuardrailEvent::count)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(Stage.INPUT, 2L),
                        org.assertj.core.groups.Tuple.tuple(Stage.TOOL, 1L));
        assertThat(summary.get(0).lastAt()).isNotNull();
        assertThat(events.summarySince(Instant.now().plusSeconds(3600))).isEmpty();
    }
}
