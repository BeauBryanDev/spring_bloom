package com.springbloom.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.springbloom.domain.model.vo.Money;

import lombok.Getter;

/**
 * Price and availability for one species. There is no product table: a bouquet
 * or garland is composed at quote time from these rows, so this is where the
 * commercial truth lives.
 *
 * The invariants mirror the flower_stock check constraints, enforced here so a
 * bad object cannot exist rather than failing at insert time.
 */
@Getter
public class FlowerStock {

    private final Long id;
    private final Long speciesId;
    private final FlowerStockStatus status;
    private final int quantity;
    private final Integer etaDays;
    private final Money basePrice;
    private final BigDecimal importPriceMultiplier;
    private final Instant updatedAt;

    public FlowerStock(
            Long id,
            Long speciesId,
            FlowerStockStatus status,
            int quantity,
            Integer etaDays,
            Money basePrice,
            BigDecimal importPriceMultiplier,
            Instant updatedAt) {

        this.speciesId = Objects.requireNonNull(speciesId, "speciesId");
        this.status = Objects.requireNonNull(status, "status");
        this.basePrice = Objects.requireNonNull(basePrice, "basePrice");
        this.importPriceMultiplier =
                Objects.requireNonNull(importPriceMultiplier, "importPriceMultiplier");

        if (quantity < 0) {
            throw new IllegalArgumentException("stock quantity cannot be negative: " + quantity);
        }
        if (etaDays != null && etaDays < 0) {
            throw new IllegalArgumentException("etaDays cannot be negative: " + etaDays);
        }
        if (importPriceMultiplier.signum() <= 0) {
            throw new IllegalArgumentException(
                    "importPriceMultiplier must be positive: " + importPriceMultiplier);
        }

        this.id = id;
        this.quantity = quantity;
        this.etaDays = etaDays;
        this.updatedAt = updatedAt;
    }

    /** base_price * import_price_multiplier, the price a customer is actually quoted. */
    public Money effectiveUnitPrice() {
        return basePrice.multiply(importPriceMultiplier);
    }

    /** Null unless the species has a lead time, so callers must handle its absence. */
    public Optional<Integer> etaDays() {
        return Optional.ofNullable(etaDays);
    }

    /** NOT_FOR_SALE species may never be quoted, whatever their quantity or price. */
    public boolean sellable() {
        return status.sellable();
    }

    /** On hand right now, as opposed to sellable with a lead time. */
    public boolean availableImmediately() {
        return status == FlowerStockStatus.IN_STOCK && quantity > 0;
    }

    /**
     * Whether the requested amount can be sold. Only IN_STOCK is limited by the
     * quantity on hand: a restock or an import is ordered to demand, and is
     * bounded by its lead time instead.
     */
    public boolean canFulfil(int requestedQuantity) {

        if (requestedQuantity <= 0) {

            throw new IllegalArgumentException("requested quantity must be positive");
        }
        if (!sellable()) {
            return false;
        }
        return status == FlowerStockStatus.IN_STOCK
                ? quantity >= requestedQuantity
                : true;
    }

    /** Absent when the flower may not be sold, so no caller can price it by forgetting a check. */
    public Optional<Money> unitPrice() {
        return sellable() ? Optional.of(effectiveUnitPrice()) : Optional.empty();
    }
}

