package com.springbloom.application.usecase;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.springbloom.domain.model.FlowerStockStatus;
import com.springbloom.domain.port.in.CheckAvailabilityUseCase;
import com.springbloom.domain.port.in.CheckAvailabilityUseCase.AvailableFlower;
import com.springbloom.domain.port.in.CheckAvailabilityUseCase.CheckAvailabilityCommand;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real flowers.json catalog against the real seeded database, so the
 * Spanish names a customer would actually type are the ones under test.
 *
 *   set -a; . ./.env; set +a; ./mvnw test
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_USER", matches = ".+")
class CheckAvailabilityServiceIntegrationTest {

    @Autowired
    private CheckAvailabilityUseCase checkAvailability;

    @Test
    @DisplayName("asking for rosas answers with the real price of the real rose")
    void answersARealCustomerPhrase() {
        List<AvailableFlower> answers = checkAvailability.check(CheckAvailabilityCommand.of("rosas"));

        assertThat(answers).isNotEmpty();
        AvailableFlower best = answers.get(0);

        assertThat(best.speciesKey()).isEqualTo("rose");
        assertThat(best.commonName()).isEqualTo("Rosa");
        assertThat(best.species().getId()).isNotNull();
        assertThat(best.unitPrice()).isPresent();
        assertThat(best.unitPrice().get().amount()).isPositive();
        assertThat(best.availability()).isNotBlank();
    }

    @Test
    @DisplayName("monkshood is found and is quotable by nobody")
    void neverPricesTheFlowerWeMustNotSell() {
        AvailableFlower monkshood = checkAvailability
                .check(CheckAvailabilityCommand.of("aconito")).get(0);

        assertThat(monkshood.speciesKey()).isEqualTo("monkshood");
        assertThat(monkshood.availability()).isEqualTo(FlowerStockStatus.NOT_FOR_SALE.label());
        assertThat(monkshood.quotable()).isFalse();
        assertThat(monkshood.unitPrice()).isEmpty();
    }

    @Test
    @DisplayName("an accented plural still finds its species in the seeded catalog")
    void foldsAccentsAgainstTheRealCatalog() {
        assertThat(checkAvailability.check(CheckAvailabilityCommand.of("nenúfares")))
                .extracting(AvailableFlower::speciesKey)
                .contains("water_lily");
    }

    @Test
    @DisplayName("a plant the store does not carry answers with nothing")
    void answersNothingForAPlantWeDoNotCarry() {
        // Not "girasoles": the store does carry sunflowers, and the matcher finds them.
        assertThat(checkAvailability.check(CheckAvailabilityCommand.of("helechos"))).isEmpty();
        assertThat(checkAvailability.check(CheckAvailabilityCommand.of("eucalipto"))).isEmpty();
    }

    @Test
    @DisplayName("every answer either carries a price or is explicitly unsellable")
    void neverLeaksAPriceForAnUnsellableFlower() {
        List<String> terms = List.of("rosa", "clavel", "astromelia", "aconito", "protea", "orquidea");

        for (String term : terms) {
            for (AvailableFlower answer : checkAvailability.check(CheckAvailabilityCommand.of(term))) {
                assertThat(answer.unitPrice().isPresent())
                        .as("%s: price present iff quotable", answer.speciesKey())
                        .isEqualTo(answer.quotable());
            }
        }
    }
}
