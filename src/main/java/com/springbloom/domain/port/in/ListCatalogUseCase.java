package com.springbloom.domain.port.in;

import java.util.List;
import java.util.Optional;

import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.vo.Money;

/**
 * The storefront listing: every species a customer may actually buy, with its
 * live price and availability. Unlike CheckAvailabilityUseCase this answers no
 * question - it is the whole shelf, ordered for display.
 */
public interface ListCatalogUseCase {

    /** The full sellable catalog. NOT_FOR_SALE species are never included. */
    List<CatalogEntry> listSellable();

    /**
     * Every species that has a stock row, NOT_FOR_SALE included. The catalog
     * page shows them so a customer can see the shelf as it is; the entry still
     * refuses to price one, so nothing can sell it by accident.
     */
    List<CatalogEntry> listAll();

    /** The first few entries, for the home page preview grid. */
    List<CatalogEntry> listFeatured(int limit);

    /**
     * One species by its key, for the product page. Empty when the key is
     * unknown or the species has no stock row - the page has nothing to show
     * either way, and the caller answers 404.
     */
    Optional<CatalogEntry> findByKey(String speciesKey);

    /**
     * A species paired with its stock row. A species with no flower_stock row
     * is absent entirely: there is no price to show and nothing to sell.
     */
    record CatalogEntry(FlowerSpecies species, FlowerStock stock) {

        public CatalogEntry {
            if (species == null || stock == null) {
                throw new IllegalArgumentException("species and stock are required");
            }
        }

        public String speciesKey() {
            return species.getSpeciesKey();
        }

        public String commonName() {
            return species.getCommonName();
        }

        public String scientificName() {
            return species.getScientificName();
        }

        /** The enum name, matching the flower_stock_status database values. */
        public String availability() {
            return stock.getStatus().name();
        }

        /** The Spanish label shown to the customer. */
        public String availabilityLabel() {
            return stock.getStatus().label();
        }

        /** Absent when the flower may not be sold, so no caller can price it. */
        public Optional<Money> unitPrice() {
            return stock.unitPrice();
        }

        public boolean availableImmediately() {
            return stock.availableImmediately();
        }

        public int quantity() {
            return stock.getQuantity();
        }

        public Optional<Integer> etaDays() {
            return stock.etaDays();
        }

        /** The three descriptive fields, absent rather than blank when unset. */
        public Optional<String> description() {
            return text(species.getDescription());
        }

        public Optional<String> symbolicMeaning() {
            return text(species.getSymbolicMeaning());
        }

        public Optional<String> originCountry() {
            return text(species.getOriginCountry());
        }

        private static Optional<String> text(String value) {
            return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
        }
    }
}
