package com.springbloom.domain.port.in;

import java.math.BigDecimal;

import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.FlowerStockStatus;

/** Admin editing of an existing flower_stock row. Never creates one. */
public interface UpdateStockUseCase {

    FlowerStock update(UpdateStockCommand command);

    record UpdateStockCommand(
            String speciesKey,
            FlowerStockStatus status,
            int quantity,
            Integer etaDays,
            BigDecimal basePrice,
            BigDecimal importPriceMultiplier) {

        public UpdateStockCommand {
            if (speciesKey == null || speciesKey.isBlank()) {
                throw new IllegalArgumentException("speciesKey is required");
            }
            if (status == null) {
                throw new IllegalArgumentException("status is required");
            }
            if (basePrice == null) {
                throw new IllegalArgumentException("basePrice is required");
            }
            if (importPriceMultiplier == null) {
                throw new IllegalArgumentException("importPriceMultiplier is required");
            }
        }
    }
}
