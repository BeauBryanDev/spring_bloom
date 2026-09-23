package com.springbloom.domain.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.FlowerStockStatus;
import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.Quotation;
import com.springbloom.domain.model.QuotationItem;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.service.QuotationComposer.Selection;
import com.springbloom.domain.service.pricing.BouquetPricingStrategy;
import com.springbloom.domain.service.pricing.GarlandPricingStrategy;
import com.springbloom.domain.service.pricing.IndividualPricingStrategy;
import com.springbloom.domain.service.pricing.PricingStrategyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The pricing rules, with no Spring and no database anywhere. */
class QuotationComposerTest {

    private final QuotationComposer composer = new QuotationComposer(
            new PricingStrategyFactory(List.of(
                    new IndividualPricingStrategy(),
                    new BouquetPricingStrategy(),
                    new GarlandPricingStrategy())));

    private static Selection selection(long id, String key, String price, int quantity) {
        return selection(id, key, price, quantity, FlowerStockStatus.IN_STOCK, 500, "1.000");
    }

    private static Selection selection(
            long id, String key, String price, int quantity,
            FlowerStockStatus status, int onHand, String multiplier) {

        FlowerSpecies species = new FlowerSpecies(
                id, key, "Nombre " + key, "Scientific " + key, "Colombia", null, null);
        FlowerStock stock = new FlowerStock(
                id, id, status, onHand, null, Money.of(price), new BigDecimal(multiplier), Instant.now());
        return new Selection(species, stock, quantity);
    }

    @Nested
    class Individual {

        @Test
        @DisplayName("a single flower is quoted at list price")
        void quotesAtListPrice() {
            QuotationItem item = composer.composeItem(
                    ProductType.INDIVIDUAL, null, List.of(selection(1L, "rose", "4448.00", 3)));

            assertThat(item.composedSubtotal()).isEqualTo(Money.of("13344.00"));
            assertThat(item.subtotal()).isEqualTo(Money.of("13344.00"));
            assertThat(item.discountAmount()).isEqualTo(Money.ZERO);
            assertThat(item.totalStems()).isEqualTo(3);
        }

        @Test
        @DisplayName("an INDIVIDUAL line cannot carry a discount or a second species")
        void rejectsBundleShapes() {
            assertThatThrownBy(() -> composer.composeItem(
                    ProductType.INDIVIDUAL, new BigDecimal("10"),
                    List.of(selection(1L, "rose", "4448.00", 3))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot carry a discount");

            assertThatThrownBy(() -> composer.composeItem(
                    ProductType.INDIVIDUAL, null,
                    List.of(selection(1L, "rose", "4448.00", 3),
                            selection(2L, "Calla", "4935.00", 2))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exactly one species");
        }
    }

    @Nested
    class Bundles {

        @Test
        @DisplayName("a bouquet sums its species, then applies the discount")
        void composesAndDiscounts() {
            QuotationItem bouquet = composer.composeItem(
                    ProductType.BOUQUET, new BigDecimal("10"),
                    List.of(selection(1L, "rose", "4448.00", 10),
                            selection(2L, "Calla", "4935.00", 5)));

            assertThat(bouquet.composedSubtotal()).isEqualTo(Money.of("69155.00"));
            assertThat(bouquet.subtotal()).isEqualTo(Money.of("62239.50"));
            assertThat(bouquet.discountAmount()).isEqualTo(Money.of("6915.50"));
            assertThat(bouquet.species()).hasSize(2);
            assertThat(bouquet.totalStems()).isEqualTo(15);
        }

        @Test
        @DisplayName("a garland prices the same way today")
        void garlandUsesItsOwnStrategy() {
            QuotationItem garland = composer.composeItem(
                    ProductType.GARLAND, new BigDecimal("20"),
                    List.of(selection(1L, "rose", "4448.00", 20)));

            assertThat(garland.composedSubtotal()).isEqualTo(Money.of("88960.00"));
            assertThat(garland.subtotal()).isEqualTo(Money.of("71168.00"));
        }

        @Test
        @DisplayName("a garland still requires an explicit discount: only BOUQUET is policy-resolved")
        void rejectsMissingDiscount() {
            assertThatThrownBy(() -> composer.composeItem(
                    ProductType.GARLAND, null, List.of(selection(1L, "rose", "4448.00", 3))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("require a discount");
        }

        @Test
        @DisplayName("a bouquet with no discount falls back to the shop's volume policy")
        void fillsMissingBouquetDiscountFromPolicy() {
            QuotationItem bouquet = composer.composeItem(
                    ProductType.BOUQUET, null, List.of(selection(1L, "rose", "4448.00", 3)));

            assertThat(bouquet.discountPercentage()).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
        }

        @Test
        @DisplayName("the same species cannot appear twice in one line")
        void rejectsDuplicateSpecies() {
            assertThatThrownBy(() -> composer.composeItem(
                    ProductType.BOUQUET, new BigDecimal("10"),
                    List.of(selection(1L, "rose", "4448.00", 3),
                            selection(1L, "rose", "4448.00", 2))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("twice");
        }
    }

    @Nested
    class Availability {

        @Test
        @DisplayName("a NOT_FOR_SALE species can never enter a quotation")
        void rejectsUnsellable() {
            assertThatThrownBy(() -> composer.composeItem(
                    ProductType.INDIVIDUAL, null,
                    List.of(selection(1L, "monkshood", "0.00", 1,
                            FlowerStockStatus.NOT_FOR_SALE, 0, "1.000"))))
                    .isInstanceOf(UnavailableSpeciesException.class)
                    .hasMessageContaining("monkshood");
        }

        @Test
        @DisplayName("more stems than are on the shelf is refused with a reason")
        void rejectsOverorder() {
            assertThatThrownBy(() -> composer.composeItem(
                    ProductType.INDIVIDUAL, null,
                    List.of(selection(1L, "rose", "4448.00", 30,
                            FlowerStockStatus.IN_STOCK, 12, "1.000"))))
                    .isInstanceOf(UnavailableSpeciesException.class)
                    .hasMessageContaining("solo quedan 12");
        }

        @Test
        @DisplayName("an import sells beyond the shelf, at its multiplied price")
        void importsSellToDemand() {
            QuotationItem item = composer.composeItem(
                    ProductType.INDIVIDUAL, null,
                    List.of(selection(1L, "king_protea", "37487.00", 4,
                            FlowerStockStatus.IMPORT_ON_REQUEST, 0, "1.400")));

            assertThat(item.species().get(0).unitPriceSnapshot())
                    .as("the multiplier is snapshotted, not the base price")
                    .isEqualTo(Money.of("52481.80"));
            assertThat(item.subtotal()).isEqualTo(Money.of("209927.20"));
        }
    }

    @Nested
    class Totals {

        @Test
        @DisplayName("a quotation totals its lines bottom-up")
        void addsUpAcrossLines() {
            QuotationItem single = composer.composeItem(
                    ProductType.INDIVIDUAL, null, List.of(selection(1L, "rose", "4448.00", 3)));
            QuotationItem bouquet = composer.composeItem(
                    ProductType.BOUQUET, new BigDecimal("10"),
                    List.of(selection(2L, "Calla", "4935.00", 10)));

            Quotation quotation = composer.compose(List.of(single, bouquet));

            assertThat(quotation.subtotal()).isEqualTo(Money.of("62694.00"));
            assertThat(quotation.totalAmount()).isEqualTo(Money.of("57759.00"));
            assertThat(quotation.discountAmount())
                    .as("subtotal minus total, matching quotation.discount_amount")
                    .isEqualTo(Money.of("4935.00"));
            assertThat(quotation.totalStems()).isEqualTo(13);
        }

        @Test
        @DisplayName("snapshots keep a quotation stable when the price list moves")
        void snapshotsSurviveRepricing() {
            Selection today = selection(1L, "rose", "4448.00", 10);
            QuotationItem item = composer.composeItem(ProductType.INDIVIDUAL, null, List.of(today));

            assertThat(item.species().get(0).unitPriceSnapshot()).isEqualTo(Money.of("4448.00"));
            assertThat(item.species().get(0).commonNameSnapshot()).isEqualTo("Nombre rose");
            assertThat(item.species().get(0).lineTotal()).isEqualTo(Money.of("44480.00"));
        }

        @Test
        @DisplayName("an empty quotation is not a quotation")
        void rejectsEmpty() {
            assertThatThrownBy(() -> composer.compose(List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
