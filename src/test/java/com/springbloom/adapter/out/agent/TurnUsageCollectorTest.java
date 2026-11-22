package com.springbloom.adapter.out.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.springbloom.domain.model.TokenUsage;

import static org.assertj.core.api.Assertions.assertThat;

class TurnUsageCollectorTest {

    @AfterEach
    void clear() {
        TurnUsageCollector.end();
    }

    @Test
    @DisplayName("the calls of one turn are summed, cache counts included")
    void sums() {
        TurnUsageCollector.begin();
        TurnUsageCollector.add("claude-haiku-4-5", 50, 20, 0, 5000);
        TurnUsageCollector.add("claude-haiku-4-5", 80, 300, 5000, 400);

        TokenUsage usage = TurnUsageCollector.end().orElseThrow();

        assertThat(usage).isEqualTo(new TokenUsage("claude-haiku-4-5", 130, 320, 5000, 5400));
    }

    @Test
    @DisplayName("a turn with no model call, or a call outside a turn, yields nothing")
    void empty() {
        TurnUsageCollector.add("m", 1, 1, 0, 0);
        assertThat(TurnUsageCollector.end()).isEmpty();

        TurnUsageCollector.begin();
        assertThat(TurnUsageCollector.end()).isEmpty();
    }

    @Test
    @DisplayName("the next turn starts from zero")
    void resets() {
        TurnUsageCollector.begin();
        TurnUsageCollector.add("m", 10, 10, 0, 0);
        TurnUsageCollector.end();

        TurnUsageCollector.begin();
        TurnUsageCollector.add("m", 1, 2, 0, 0);

        assertThat(TurnUsageCollector.end().orElseThrow().total()).isEqualTo(3);
    }
}
