package com.springbloom.domain.port.in;

import java.util.List;
import java.util.Optional;

import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.vo.Money;

/**
 * Answers "do you have X, and how much is it" without a photo and without
 * composing a quotation. This is the agent's cheapest tool and the one it will
 * reach for most: a customer asks about a flower long before ordering one.
 */
public interface CheckAvailabilityUseCase {

    /**
     * Best matches for what the customer typed, best first. An empty list means
     * nothing in the catalog resembles the term - a normal answer the agent
     * relays, not an error.
     */
    List<AvailableFlower> check(CheckAvailabilityCommand command);

    /**
     * @param term free text as the customer wrote it, or an exact species_key
     * @param quantity how many stems they asked about, null when they did not say
     * @param limit how many alternatives to offer
     */
    record CheckAvailabilityCommand(String term, Integer quantity, int limit) {

        private static final int DEFAULT_LIMIT = 5;

        public CheckAvailabilityCommand {
            
            if (term == null || term.isBlank()) {
                throw new IllegalArgumentException("A search term is required");
            }

            if (quantity != null && quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive: " + quantity);
            }

            if (limit <= 0) {
                throw new IllegalArgumentException("limit must be positive: " + limit);
            }
        }

        /** What the agent uses when the customer just names a flower. */
        public static CheckAvailabilityCommand of(String term) {
            return new CheckAvailabilityCommand(term, null, DEFAULT_LIMIT);
        }

        public static CheckAvailabilityCommand of(String term, int quantity) {
            return new CheckAvailabilityCommand(term, quantity, DEFAULT_LIMIT);
        }

        public Optional<Integer> requestedQuantity() {
            return Optional.ofNullable(quantity);
        }
    }

    /**
     * A species with its live commercial state. A flower we do not sell still
     * comes back, marked unquotable: the agent has to be able to say "esa no la
     * vendemos" rather than pretend it does not exist.
     */
    record AvailableFlower(FlowerSpecies species, FlowerStock stock, boolean fulfilsRequest) {

        public AvailableFlower {
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

        /** False for NOT_FOR_SALE: nothing downstream may quote a price for it. */
        public boolean quotable() {
            return stock.sellable();
        }

        public boolean availableImmediately() {
            return stock.availableImmediately();
        }

        /** Absent unless the flower may actually be sold, mirroring IdentifiedFlower. */
        public Optional<Money> unitPrice() {
            return stock.unitPrice();
        }

        public Optional<Integer> etaDays() {
            return stock.etaDays();
        }

        /** The Spanish label the agent reads back to the customer. */
        public String availability() {
            return stock.getStatus().label();
        }
    }
}
