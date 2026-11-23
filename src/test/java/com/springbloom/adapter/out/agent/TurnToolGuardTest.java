package com.springbloom.adapter.out.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.springbloom.domain.service.guardrail.OutputGuard;

class TurnToolGuardTest {

    @Test
    @DisplayName("calls past the limit are refused with a reason the model can relay")
    void limitsCalls() {
        TurnToolGuard guard = new TurnToolGuard(2);

        assertThat(guard.admit("findFlowers", "{}")).isNull();
        assertThat(guard.admit("findFlowers", "{}")).isNull();
        assertThat(guard.admit("findFlowers", "{}")).contains("refused");
        assertThat(guard.refusals()).isEqualTo(1);
    }

    @Test
    @DisplayName("a write with identical arguments runs once per turn")
    void refusesDuplicateWrites() {
        TurnToolGuard guard = new TurnToolGuard(8);

        assertThat(guard.admit("quoteSingleFlower", "{\"q\":1}")).isNull();
        assertThat(guard.admit("quoteSingleFlower", "{\"q\":1}")).contains("ya se realizó");
        assertThat(guard.admit("quoteSingleFlower", "{\"q\":2}")).isNull();
    }

    @Test
    @DisplayName("reads may repeat")
    void readsRepeat() {
        TurnToolGuard guard = new TurnToolGuard(8);

        assertThat(guard.admit("findFlowers", "{}")).isNull();
        assertThat(guard.admit("findFlowers", "{}")).isNull();
    }

    @Test
    @DisplayName("amounts and numbers in tool output become facts a reply may use")
    void remembersToolNumbers() {
        TurnToolGuard guard = new TurnToolGuard(8);
        guard.observe("{\"quoted\":true,\"quotationNumber\":\"COT-20260924-11416\","
                + "\"totalCop\":\"85689.00\",\"unitPriceCop\":4448.00}");

        assertThat(OutputGuard.check("COT-20260924-11416: $85.689 COP, rosas a $4.448.", guard.facts()).passed())
                .isTrue();
        assertThat(OutputGuard.check("Son $99.000.", guard.facts()).passed()).isFalse();
    }
}
