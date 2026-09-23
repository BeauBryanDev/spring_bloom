package com.springbloom.adapter.out.persistence.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.FlowerStockStatus;
import com.springbloom.domain.port.out.FlowerStockRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hits the real seeded database, so it needs the environment run.sh loads:
 *
 *   set -a; . ./.env; set +a; ./mvnw test
 *
 * Without DB_USER the whole class is skipped rather than failing the build.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_USER", matches = ".+")
class FlowerStockRepositoryAdapterTest {

    @Autowired
    private FlowerStockRepository stockRepository;

    @Test
    @DisplayName("the join to flower_species resolves stock from a model label")
    void findsStockBySpeciesKey() {
        Optional<FlowerStock> rose = stockRepository.findBySpeciesKey("rose");

        assertThat(rose).isPresent();
        assertThat(rose.get().getSpeciesId()).isNotNull();
        assertThat(rose.get().getStatus()).isNotNull();
        assertThat(rose.get().effectiveUnitPrice().amount()).isPositive();
    }

    @Test
    @DisplayName("an unknown label finds nothing rather than failing")
    void unknownKeyIsEmpty() {
        assertThat(stockRepository.findBySpeciesKey("not_a_flower")).isEmpty();
    }

    @Test
    @DisplayName("species_key matching is case sensitive, as the catalog demands")
    void keyMatchingIsCaseSensitive() {
        assertThat(stockRepository.findBySpeciesKey("Carnation")).isPresent();
        assertThat(stockRepository.findBySpeciesKey("carnation")).isEmpty();
    }

    @Test
    @DisplayName("the Postgres enum reads back into every FlowerStockStatus constant")
    void readsEveryStatusValue() {
        List<FlowerStock> all = stockRepository.findAll();
        assertThat(all).hasSize(90);

        Map<FlowerStockStatus, Long> byStatus = all.stream()
                .collect(Collectors.groupingBy(FlowerStock::getStatus, Collectors.counting()));

        assertThat(byStatus).containsOnlyKeys(FlowerStockStatus.values());
        assertThat(byStatus.get(FlowerStockStatus.IN_STOCK)).isEqualTo(40);
        assertThat(byStatus.get(FlowerStockStatus.INCOMING_RESTOCK)).isEqualTo(30);
        assertThat(byStatus.get(FlowerStockStatus.IMPORT_ON_REQUEST)).isEqualTo(10);
        assertThat(byStatus.get(FlowerStockStatus.NOT_FOR_SALE)).isEqualTo(10);
    }

    @Test
    @DisplayName("the seed invariants the domain rules depend on still hold")
    void seedInvariantsHold() {
        List<FlowerStock> all = stockRepository.findAll();

        assertThat(all)
                .as("only IN_STOCK carries quantity")
                .allSatisfy(stock -> {
                    if (stock.getStatus() != FlowerStockStatus.IN_STOCK) {
                        assertThat(stock.getQuantity()).isZero();
                    }
                });

        assertThat(all)
                .as("only IMPORT_ON_REQUEST is marked up")
                .allSatisfy(stock -> {
                    if (stock.getStatus() != FlowerStockStatus.IMPORT_ON_REQUEST) {
                        assertThat(stock.getImportPriceMultiplier())
                                .isEqualByComparingTo("1.000");
                    }
                });
    }
}
