package com.springbloom.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.springbloom.domain.model.vo.Money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure domain rules: no Spring, no mocks, no database. */
class FlowerStockTest {

    private static FlowerStock stock(FlowerStockStatus status, int quantity, String price, String multiplier) {
        return new FlowerStock(
                1L, 42L, status, quantity, null,
                Money.of(price), new BigDecimal(multiplier), Instant.now());
    }

    @Nested
    class Pricing {

        @Test
        @DisplayName("effective price is base price times the import multiplier")
        void appliesImportMultiplier() {
            FlowerStock imported = stock(FlowerStockStatus.IMPORT_ON_REQUEST, 0, "12.50", "1.400");

            assertThat(imported.effectiveUnitPrice()).isEqualTo(Money.of("17.50"));
        }

        @Test
        @DisplayName("a multiplier of one leaves the base price untouched")
        void domesticPriceIsBasePrice() {
            FlowerStock domestic = stock(FlowerStockStatus.IN_STOCK, 10, "4.50", "1.000");

            assertThat(domestic.effectiveUnitPrice()).isEqualTo(Money.of("4.50"));
        }

        @Test
        @DisplayName("prices stay at two decimals, rounded HALF_UP")
        void roundsToTwoDecimals() {
            FlowerStock odd = stock(FlowerStockStatus.IMPORT_ON_REQUEST, 0, "3.33", "1.155");

            assertThat(odd.effectiveUnitPrice()).isEqualTo(Money.of("3.85"));
            assertThat(odd.unitPrice()).contains(Money.of("3.85"));
        }
    }

    @Nested
    class Availability {

        @Test
        @DisplayName("NOT_FOR_SALE can never be quoted, whatever the quantity")
        void notForSaleIsNeverFulfillable() {
            FlowerStock poisonous = stock(FlowerStockStatus.NOT_FOR_SALE, 500, "9.00", "1.000");

            assertThat(poisonous.sellable()).isFalse();
            assertThat(poisonous.canFulfil(1)).isFalse();
            assertThat(poisonous.availableImmediately()).isFalse();
        }

        @Test
        @DisplayName("IN_STOCK is limited by the quantity on hand")
        void inStockIsBoundedByQuantity() {
            FlowerStock roses = stock(FlowerStockStatus.IN_STOCK, 12, "4.50", "1.000");

            assertThat(roses.canFulfil(12)).isTrue();
            assertThat(roses.canFulfil(13)).isFalse();
            assertThat(roses.availableImmediately()).isTrue();
        }

        @Test
        @DisplayName("restock and import sell to demand, not from the shelf")
        void leadTimeStatusesIgnoreQuantity() {
            FlowerStock incoming = stock(FlowerStockStatus.INCOMING_RESTOCK, 0, "6.00", "1.000");
            FlowerStock imported = stock(FlowerStockStatus.IMPORT_ON_REQUEST, 0, "6.00", "1.250");

            assertThat(incoming.canFulfil(200)).isTrue();
            assertThat(imported.canFulfil(200)).isTrue();

            assertThat(incoming.availableImmediately())
                    .as("sellable, but not on the shelf today")
                    .isFalse();
        }

        @Test
        @DisplayName("IN_STOCK with nothing left is sellable but not immediate")
        void emptyShelfIsNotImmediate() {
            FlowerStock sold = stock(FlowerStockStatus.IN_STOCK, 0, "4.50", "1.000");

            assertThat(sold.availableImmediately()).isFalse();
            assertThat(sold.canFulfil(1)).isFalse();
        }
    }

    @Nested
    class Invariants {

        @Test
        @DisplayName("the check constraints are enforced in the constructor")
        void rejectsInvalidState() {
            assertThatThrownBy(() -> stock(FlowerStockStatus.IN_STOCK, -1, "4.50", "1.000"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("quantity");

            assertThatThrownBy(() -> stock(FlowerStockStatus.IN_STOCK, 1, "4.50", "0.000"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("importPriceMultiplier");

            assertThatThrownBy(() -> Money.of("-1.00"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a missing lead time is absent, not null")
        void etaIsOptional() {
            FlowerStock noEta = stock(FlowerStockStatus.IN_STOCK, 5, "4.50", "1.000");
            FlowerStock withEta = new FlowerStock(
                    1L, 42L, FlowerStockStatus.INCOMING_RESTOCK, 0, 7,
                    Money.of("4.50"), BigDecimal.ONE, Instant.now());

            assertThat(noEta.etaDays()).isEmpty();
            assertThat(withEta.etaDays()).contains(7);
        }

        @Test
        @DisplayName("asking for a non-positive quantity is a programming error")
        void rejectsNonPositiveRequest() {
            FlowerStock roses = stock(FlowerStockStatus.IN_STOCK, 12, "4.50", "1.000");

            assertThatThrownBy(() -> roses.canFulfil(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
