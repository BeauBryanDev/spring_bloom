package com.springbloom.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.FlowerStockStatus;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.in.CheckAvailabilityUseCase.AvailableFlower;
import com.springbloom.domain.port.in.CheckAvailabilityUseCase.CheckAvailabilityCommand;
import com.springbloom.domain.port.out.FlowerCatalogPort;
import com.springbloom.domain.port.out.FlowerSpeciesRepository;
import com.springbloom.domain.port.out.FlowerStockRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Matching comes from the real catalog logic; the database is mocked. */
class CheckAvailabilityServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-26T15:00:00Z");

    private FlowerCatalogPort catalog;
    private FlowerSpeciesRepository speciesRepository;
    private FlowerStockRepository stockRepository;
    private CheckAvailabilityService service;

    private final List<FlowerSpecies> catalogEntries = new ArrayList<>();

    @BeforeEach
    void setUp() {
        catalog = mock(FlowerCatalogPort.class);
        speciesRepository = mock(FlowerSpeciesRepository.class);
        stockRepository = mock(FlowerStockRepository.class);
        service = new CheckAvailabilityService(catalog, speciesRepository, stockRepository);

        when(catalog.all()).thenReturn(catalogEntries);
        when(speciesRepository.findBySpeciesKey(anyString())).thenReturn(Optional.empty());
        when(stockRepository.findBySpeciesKey(anyString())).thenReturn(Optional.empty());
    }

    /** Adds a species to both the catalog and the mocked database. */
    private void stocked(long id, String key, String commonName, String price,
                         FlowerStockStatus status, int onHand, String multiplier) {

        catalogEntries.add(FlowerSpecies.catalogEntry(key, commonName, "Scientific " + key));

        FlowerSpecies persisted = new FlowerSpecies(
                id, key, commonName, "Scientific " + key, "Colombia", null, null);
        FlowerStock stock = new FlowerStock(
                id, id, status, onHand, status == FlowerStockStatus.INCOMING_RESTOCK ? 5 : null,
                Money.of(price), new BigDecimal(multiplier), NOW);

        when(speciesRepository.findBySpeciesKey(key)).thenReturn(Optional.of(persisted));
        when(stockRepository.findBySpeciesKey(key)).thenReturn(Optional.of(stock));
    }

    private void rose() {
        stocked(1L, "rose", "Rosa", "4448.00", FlowerStockStatus.IN_STOCK, 200, "1.000");
    }

    @Test
    @DisplayName("a customer's plural is answered with the live price")
    void answersWithLiveCommercialState() {
        rose();

        List<AvailableFlower> answers = service.check(CheckAvailabilityCommand.of("rosas"));

        assertThat(answers).hasSize(1);
        AvailableFlower rose = answers.get(0);

        assertThat(rose.speciesKey()).isEqualTo("rose");
        assertThat(rose.commonName()).isEqualTo("Rosa");
        assertThat(rose.species().getId()).as("the persisted row, not the catalog's").isEqualTo(1L);
        assertThat(rose.quotable()).isTrue();
        assertThat(rose.availableImmediately()).isTrue();
        assertThat(rose.unitPrice()).contains(Money.of("4448.00"));
        assertThat(rose.availability()).isEqualTo(FlowerStockStatus.IN_STOCK.label());
        assertThat(rose.fulfilsRequest()).isTrue();
    }

    @Test
    @DisplayName("the import multiplier is baked into the price the agent quotes")
    void appliesTheImportMultiplier() {
        stocked(2L, "king_protea", "Protea rey", "37487.00",
                FlowerStockStatus.IMPORT_ON_REQUEST, 0, "1.400");

        AvailableFlower protea = service.check(CheckAvailabilityCommand.of("protea")).get(0);

        assertThat(protea.unitPrice()).contains(Money.of("52481.80"));
        assertThat(protea.availableImmediately()).isFalse();
        assertThat(protea.quotable()).isTrue();
    }

    @Test
    @DisplayName("a flower we do not sell is still answered, with no price attached")
    void reportsUnsellableFlowersWithoutAPrice() {
        stocked(3L, "monkshood", "Aconito", "1000.00", FlowerStockStatus.NOT_FOR_SALE, 0, "1.000");

        AvailableFlower monkshood = service.check(CheckAvailabilityCommand.of("aconito")).get(0);

        assertThat(monkshood.quotable()).isFalse();
        assertThat(monkshood.unitPrice())
                .as("no caller can quote a price for a flower we may not sell")
                .isEmpty();
        assertThat(monkshood.fulfilsRequest()).isFalse();
    }

    @Test
    @DisplayName("an asked-for quantity is checked against what is on hand")
    void checksTheRequestedQuantity() {
        stocked(4L, "rose", "Rosa", "4448.00", FlowerStockStatus.IN_STOCK, 10, "1.000");

        assertThat(service.check(CheckAvailabilityCommand.of("rosas", 10)).get(0).fulfilsRequest())
                .isTrue();
        assertThat(service.check(CheckAvailabilityCommand.of("rosas", 11)).get(0).fulfilsRequest())
                .as("still answered, just not fulfillable")
                .isFalse();
    }

    @Test
    @DisplayName("alternatives come back ranked, best match first")
    void ranksAlternatives() {
        rose();
        stocked(5L, "desert-rose", "Rosa del desierto", "9000.00",
                FlowerStockStatus.IN_STOCK, 50, "1.000");

        assertThat(service.check(CheckAvailabilityCommand.of("rosas")))
                .extracting(AvailableFlower::speciesKey)
                .containsExactly("rose", "desert-rose");
    }

    @Test
    @DisplayName("a flower we do not carry is an empty answer the agent relays")
    void returnsEmptyForAnUnknownFlower() {
        rose();

        assertThat(service.check(CheckAvailabilityCommand.of("helechos"))).isEmpty();
        verify(speciesRepository, never()).findBySpeciesKey("helecho");
    }

    @Test
    @DisplayName("a catalog entry with no stock row is left out rather than priced at nothing")
    void skipsSpeciesWithNoStockRow() {
        catalogEntries.add(FlowerSpecies.catalogEntry("orphan", "Huerfana", "Orphanus"));
        when(speciesRepository.findBySpeciesKey("orphan")).thenReturn(
                Optional.of(new FlowerSpecies(9L, "orphan", "Huerfana", "Orphanus", null, null, null)));

        assertThat(service.check(CheckAvailabilityCommand.of("huerfana"))).isEmpty();
    }

    @Test
    @DisplayName("the command rejects nonsense before the catalog is touched")
    void guardsItsCommand() {
        assertThatThrownBy(() -> CheckAvailabilityCommand.of(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CheckAvailabilityCommand.of("rosas", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CheckAvailabilityCommand("rosas", null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
