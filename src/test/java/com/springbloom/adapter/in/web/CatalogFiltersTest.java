package com.springbloom.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.springbloom.adapter.in.web.CatalogFilters.Availability;
import com.springbloom.adapter.in.web.CatalogFilters.PriceBand;
import com.springbloom.adapter.in.web.CatalogFilters.SortOrder;
import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.FlowerStockStatus;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.in.ListCatalogUseCase.CatalogEntry;

import static org.assertj.core.api.Assertions.assertThat;

/** How the catalog page narrows the shelf. Pure: no Spring, no database. */
class CatalogFiltersTest {

    private static final CatalogEntry CHEAP =
            entry("clavel", "Clavel", FlowerStockStatus.IN_STOCK, "3200.00");
    private static final CatalogEntry MID =
            entry("rose", "Rosa", FlowerStockStatus.INCOMING_RESTOCK, "7500.00");
    private static final CatalogEntry PRICEY =
            entry("king_protea", "Protea", FlowerStockStatus.IMPORT_ON_REQUEST, "52481.80");
    private static final CatalogEntry UNPRICED =
            entry("monkshood", "Aconito", FlowerStockStatus.NOT_FOR_SALE, "9000.00");

    private static final List<CatalogEntry> SHELF = List.of(PRICEY, UNPRICED, CHEAP, MID);

    @Test
    @DisplayName("an availability pill keeps only that status")
    void filtersByStatus() {
        assertThat(SHELF.stream().filter(Availability.IN_STOCK.matches()).toList())
                .containsExactly(CHEAP);
        assertThat(SHELF.stream().filter(Availability.TODOS.matches()).toList())
                .hasSize(4);
    }

    @Test
    @DisplayName("a price band is a half-open range, so the bands do not overlap")
    void filtersByPriceBand() {
        assertThat(SHELF.stream().filter(PriceBand.UNDER_5000.matches()).toList())
                .containsExactly(CHEAP);
        assertThat(SHELF.stream().filter(PriceBand.FROM_5000_TO_10000.matches()).toList())
                .containsExactly(MID);
        assertThat(SHELF.stream().filter(PriceBand.OVER_10000.matches()).toList())
                .containsExactly(PRICEY);
    }

    @Test
    @DisplayName("a flower with no price is in no band but TODOS")
    void unpricedIsInNoBand() {
        assertThat(SHELF.stream().filter(PriceBand.OVER_10000.matches()).toList())
                .doesNotContain(UNPRICED);
        assertThat(SHELF.stream().filter(PriceBand.UNDER_5000.matches()).toList())
                .doesNotContain(UNPRICED);
        assertThat(SHELF.stream().filter(PriceBand.TODOS.matches()).toList())
                .contains(UNPRICED);
    }

    @Test
    @DisplayName("sorting by price puts what cannot be priced last, either direction")
    void sortsUnpricedLast() {
        assertThat(SHELF.stream().sorted(SortOrder.PRECIO_ASC.comparator()).toList())
                .containsExactly(CHEAP, MID, PRICEY, UNPRICED);
        assertThat(SHELF.stream().sorted(SortOrder.PRECIO_DESC.comparator()).toList())
                .containsExactly(PRICEY, MID, CHEAP, UNPRICED);
    }

    @Test
    @DisplayName("the default order is alphabetical, accents and case aside")
    void sortsByName() {
        assertThat(SHELF.stream().sorted(SortOrder.NOMBRE_ASC.comparator()).toList())
                .containsExactly(UNPRICED, CHEAP, PRICEY, MID);
    }

    @Test
    @DisplayName("a missing or unknown parameter falls back rather than failing the page")
    void parseFallsBack() {
        assertThat(CatalogFilters.parse(Availability.class, null, Availability.TODOS))
                .isEqualTo(Availability.TODOS);
        assertThat(CatalogFilters.parse(Availability.class, "  ", Availability.TODOS))
                .isEqualTo(Availability.TODOS);
        assertThat(CatalogFilters.parse(SortOrder.class, "<script>", SortOrder.NOMBRE_ASC))
                .isEqualTo(SortOrder.NOMBRE_ASC);
        assertThat(CatalogFilters.parse(SortOrder.class, " precio_desc ", SortOrder.NOMBRE_ASC))
                .isEqualTo(SortOrder.PRECIO_DESC);
    }

    private static CatalogEntry entry(
            String key, String commonName, FlowerStockStatus status, String price) {

        FlowerSpecies species =
                new FlowerSpecies(1L, key, commonName, key + " spp.", "Colombia", null, null);
        FlowerStock stock = new FlowerStock(
                1L, 1L, status, status == FlowerStockStatus.IN_STOCK ? 100 : 0, null,
                Money.of(price), new BigDecimal("1.000"), Instant.now());
        return new CatalogEntry(species, stock);
    }
}
