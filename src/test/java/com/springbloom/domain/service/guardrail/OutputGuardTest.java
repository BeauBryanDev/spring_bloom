package com.springbloom.domain.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OutputGuardTest {

    private static final TurnFacts QUOTED = TurnFacts.none()
            .withDocumentNumber("COT-20260924-11416")
            .withAmount(new BigDecimal("85689.00"))
            .withAmount(new BigDecimal("4448.00"));

    @Test
    @DisplayName("passes a reply that only repeats what the tools returned")
    void passesBackedReply() {
        String reply = "Su cotización COT-20260924-11416 queda en $85.689 COP, con rosas a 4.448 COP por tallo.";

        assertThat(OutputGuard.check(reply, QUOTED).passed()).isTrue();
    }

    @Test
    @DisplayName("blocks a document number no tool returned")
    void blocksInventedNumber() {
        GuardVerdict verdict = OutputGuard.check("Su cotización es COT-20260924-99999.", QUOTED);

        assertThat(verdict.rule()).isEqualTo("invented_document_number");
    }

    @Test
    @DisplayName("accepts a number found earlier in the conversation")
    void acceptsNumberFromHistory() {
        TurnFacts known = TurnFacts.harvest(List.of("Su pedido ORD-20260101-12345 está registrado"));

        assertThat(OutputGuard.check("Sobre su pedido ord-20260101-12345...", known).passed()).isTrue();
    }

    @Test
    @DisplayName("blocks an amount no tool returned, in either notation")
    void blocksUnbackedAmount() {
        assertThat(OutputGuard.check("Serían $90.000 en total.", QUOTED).rule()).isEqualTo("unbacked_amount");
        assertThat(OutputGuard.check("Son 90000 pesos aproximadamente.", QUOTED).rule()).isEqualTo("unbacked_amount");
    }

    @Test
    @DisplayName("compares amounts in whole pesos, rounded half up")
    void roundsAmounts() {
        TurnFacts known = TurnFacts.none().withAmount(new BigDecimal("82303.80"));

        assertThat(OutputGuard.check("El total es $82.304 COP.", known).passed()).isTrue();
    }

    @Test
    @DisplayName("ignores numbers that are not money")
    void ignoresStemsAndPercentages() {
        assertThat(OutputGuard.check("Son 15 tallos con 15% de descuento por volumen.", QUOTED).passed()).isTrue();
    }

    @Test
    @DisplayName("accepts amounts harvested from a policy passage")
    void acceptsPolicyAmounts() {
        TurnFacts known = TurnFacts.harvest(List.of("El envío cuesta $15.000 en Bogotá."));

        assertThat(OutputGuard.check("El envío cuesta $15.000.", known).passed()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Le reembolsaremos el dinero hoy mismo.",
            "Le devolvemos su dinero sin problema.",
            "Le enviaremos otro ramo mañana.",
            "Le ofrezco un descuento especial.",
            "Puedo darle un descuento si compra hoy.",
            "Le responderemos en 2 horas.",
            "Envíeme el número de su tarjeta para continuar.",
            "Soy Claude, un modelo de Anthropic.",
            "Usé la herramienta quoteBouquet para eso."
    })
    @DisplayName("blocks promises, card requests and leaks")
    void blocksBrokenRules(String reply) {
        assertThat(OutputGuard.check(reply, QUOTED).passed()).as(reply).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "No puedo prometerle un reembolso; el equipo revisará su caso.",
            "El equipo comercial decidirá y se lo confirmará.",
            "Puede reportar el daño dentro de las 24 horas siguientes a la entrega.",
            "El pago se hace en la página, nunca por este chat."
    })
    @DisplayName("lets honest wording about refunds and policy through")
    void letsHonestWordingThrough(String reply) {
        assertThat(OutputGuard.check(reply, QUOTED).passed()).as(reply).isTrue();
    }
}
