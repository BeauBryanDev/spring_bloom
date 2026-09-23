package com.springbloom.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.FlowerStockStatus;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.in.ListCatalogUseCase.CatalogEntry;
import com.springbloom.domain.port.out.FlowerSpeciesRepository;
import com.springbloom.domain.port.out.FlowerStockRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The storefront listing. Repositories are mocked; the join is this service's own. */
class ListCatalogServiceTest {

    private FlowerSpeciesRepository speciesRepository;
    private FlowerStockRepository stockRepository;
    private ListCatalogService service;

    @BeforeEach
    void setUp() {
        speciesRepository = mock(FlowerSpeciesRepository.class);
        stockRepository = mock(FlowerStockRepository.class);
        service = new ListCatalogService(speciesRepository, stockRepository);

        when(speciesRepository.findAll()).thenReturn(List.of(
                species(1L, "rose", "Rosa"),
                species(2L, "monkshood", "Aconito"),
                species(3L, "king_protea", "Protea"),
                species(4L, "ghost_flower", "Fantasma")));

        when(stockRepository.findAll()).thenReturn(List.of(
                stock(1L, FlowerStockStatus.IN_STOCK, 40),
                stock(2L, FlowerStockStatus.NOT_FOR_SALE, 0),
                stock(3L, FlowerStockStatus.IMPORT_ON_REQUEST, 0)));
    }

    @Test
    @DisplayName("the sellable listing leaves out what may not be sold")
    void hidesWhatCannotBeSold() {
        assertThat(service.listSellable())
                .extracting(CatalogEntry::commonName)
                .containsExactlyInAnyOrder("Rosa", "Protea");
    }

    @Test
    @DisplayName("the full listing keeps NOT_FOR_SALE, and still refuses to price it")
    void showsTheWholeShelf() {
        List<CatalogEntry> all = service.listAll();

        assertThat(all).extracting(CatalogEntry::commonName)
                .containsExactlyInAnyOrder("Rosa", "Aconito", "Protea");
        assertThat(all).filteredOn(entry -> entry.commonName().equals("Aconito"))
                .allSatisfy(entry -> assertThat(entry.unitPrice()).isEmpty());
    }

    @Test
    @DisplayName("a species with no stock row is absent from both listings")
    void skipsSpeciesWithNoStockRow() {
        assertThat(service.listAll()).extracting(CatalogEntry::commonName)
                .doesNotContain("Fantasma");
        assertThat(service.listSellable()).extracting(CatalogEntry::commonName)
                .doesNotContain("Fantasma");
    }

    @Test
    @DisplayName("what is on hand is listed first, so the shelf reads the same every visit")
    void ordersWhatIsOnHandFirst() {
        assertThat(service.listAll()).first()
                .extracting(CatalogEntry::commonName)
                .isEqualTo("Rosa");
    }

    @Test
    @DisplayName("the featured grid takes the first few, and refuses a nonsense limit")
    void limitsTheFeaturedGrid() {
        assertThat(service.listFeatured(1)).hasSize(1);
        assertThatThrownBy(() -> service.listFeatured(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static FlowerSpecies species(Long id, String key, String commonName) {
        return new FlowerSpecies(id, key, commonName, key + " spp.", "Colombia", null, null);
    }

    private static FlowerStock stock(Long speciesId, FlowerStockStatus status, int quantity) {
        return new FlowerStock(
                speciesId, speciesId, status, quantity, null,
                Money.of("4000.00"), new BigDecimal("1.000"), Instant.now());
    }

    @Test
    @DisplayName("findByKey returns the species with its stock")
    void findByKeyReturnsEntry() {
        when(speciesRepository.findBySpeciesKey("rose")).thenReturn(Optional.of(species(1L, "rose", "Rosa")));
        when(stockRepository.findBySpeciesKey("rose"))
                .thenReturn(Optional.of(stock(1L, FlowerStockStatus.IN_STOCK, 10)));

        assertThat(service.findByKey("rose"))
                .isPresent()
                .get()
                .satisfies(entry -> assertThat(entry.commonName()).isEqualTo("Rosa"));
    }

    @Test
    @DisplayName("an unknown key is empty, not an exception: the page answers 404")
    void unknownKeyIsEmpty() {
        when(speciesRepository.findBySpeciesKey("nope")).thenReturn(Optional.empty());

        assertThat(service.findByKey("nope")).isEmpty();
    }

    @Test
    @DisplayName("a species with no stock row is empty: nothing to price, nothing to show")
    void speciesWithoutStockIsEmpty() {
        when(speciesRepository.findBySpeciesKey("rose")).thenReturn(Optional.of(species(1L, "rose", "Rosa")));
        when(stockRepository.findBySpeciesKey("rose")).thenReturn(Optional.empty());

        assertThat(service.findByKey("rose")).isEmpty();
    }

    @Test
    @DisplayName("a blank key never reaches the repository")
    void blankKeyShortCircuits() {
        assertThat(service.findByKey("  ")).isEmpty();
        assertThat(service.findByKey(null)).isEmpty();
        verifyNoInteractions(speciesRepository);
    }
}
