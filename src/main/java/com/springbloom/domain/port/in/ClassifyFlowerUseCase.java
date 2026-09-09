package com.springbloom.domain.port.in;

import java.util.Optional;

import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.vo.Money;

/**
 * Identifies the flower in a customer photo and resolves it to a persisted
 * species. The vision model recognizes one flower per image, so this answers
 * with at most one species.
 */
public interface ClassifyFlowerUseCase {

    /**
     * Empty when nothing scored above the confidence threshold, or when the
     * recognized species is not in the database.
     */
    Optional<IdentifiedFlower> identify(ClassifyFlowerCommand command);

    /**
     * sessionKey ties the request to an anonymous conversation; there is no
     * customer until a quotation needs one.
     */
    record ClassifyFlowerCommand(byte[] imageBytes, String sessionKey) {

        public ClassifyFlowerCommand {
            if (imageBytes == null || imageBytes.length == 0) {
                throw new IllegalArgumentException("An image is required");
            }
            if (sessionKey == null || sessionKey.isBlank()) {
                throw new IllegalArgumentException("A session key is required");
            }
        }
    }

    /**
     * The persisted species behind a detection, with its commercial state.
     * Stock is absent when the species has no flower_stock row, which the schema
     * allows even though the seed gives every species one.
     */
    record IdentifiedFlower(
            FlowerSpecies species,
            double confidence,
            Optional<FlowerStock> stock,
            boolean trusted) {

        public IdentifiedFlower {
            if (species == null) {
                throw new IllegalArgumentException("species is required");
            }
            stock = stock == null ? Optional.empty() : stock;
        }

        public String speciesKey() {
            return species.getSpeciesKey();
        }

        /**
         * The confidence as a whole percentage, rounded half-up.
         */
        public int confidencePercent() {
            return (int) Math.round(confidence * 100);
        }

        /** False for NOT_FOR_SALE and for a species with no stock row at all. */
        public boolean quotable() {
            return stock().map(FlowerStock::sellable).orElse(false);
        }

        /** Absent unless the flower may actually be sold, so no price can leak for one that may not. */
        public Optional<Money> unitPrice() {
            return stock().flatMap(FlowerStock::unitPrice);
        }
    }
}
