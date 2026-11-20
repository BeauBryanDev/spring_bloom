package com.springbloom.domain.model;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.springbloom.domain.model.vo.Money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowerOrderTest {

    private static Quotation quotation() {
        QuotationItemSpecies roses = new QuotationItemSpecies(1L, "Rosa", 10, Money.of("1000"), Money.of("10000"));
        QuotationItemSpecies lilies = new QuotationItemSpecies(2L, "Lirio", 5, Money.of("2000"), Money.of("10000"));
        QuotationItem bouquet = new QuotationItem(ProductType.BOUQUET, new BigDecimal("15.00"),
                List.of(roses, lilies), Money.of("20000"), Money.of("17000"));
        QuotationItem single = new QuotationItem(ProductType.INDIVIDUAL, null,
                List.of(new QuotationItemSpecies(1L, "Rosa", 3, Money.of("1000"), Money.of("3000"))),
                Money.of("3000"), Money.of("3000"));

        return new Quotation(7L, "COT-20260101-00001", null, null, QuotationStatus.DRAFT,
                List.of(bouquet, single), Money.of("23000"), Money.of("3000"), Money.of("20000"), null);
    }

    @Test
    @DisplayName("an order copies the quotation's money and snapshots rather than recomputing them")
    void copiesQuotation() {
        FlowerOrder order = FlowerOrder.fromQuotation(quotation(), 9L);

        assertThat(order.persisted()).isFalse();
        assertThat(order.orderNumber()).isNull();
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.quotationId()).isEqualTo(7L);
        assertThat(order.customerId()).isEqualTo(9L);
        assertThat(order.totalAmount()).isEqualTo(Money.of("20000"));
        assertThat(order.discountAmount()).isEqualTo(Money.of("3000"));
        assertThat(order.items()).hasSize(2);
        assertThat(order.items().get(0).species().get(0).commonNameSnapshot()).isEqualTo("Rosa");
    }

    @Test
    @DisplayName("stems are summed per species across lines, which is what stock must give up")
    void sumsStemsPerSpecies() {
        FlowerOrder order = FlowerOrder.fromQuotation(quotation(), 9L);

        assertThat(order.stemsBySpecies()).containsEntry(1L, 13).containsEntry(2L, 5).hasSize(2);
    }

    @Test
    @DisplayName("a bouquet line without a discount is refused, matching the quotation rule")
    void bundleNeedsDiscount() {
        OrderItemSpecies line = new OrderItemSpecies(1L, "Rosa", 1, Money.of("1000"), Money.of("1000"));

        assertThatThrownBy(() -> new OrderItem(ProductType.BOUQUET, null, List.of(line), Money.of("1000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderItem(ProductType.INDIVIDUAL, BigDecimal.TEN, List.of(line), Money.of("1000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a customer needs a name, phone and address; email is optional")
    void customerValidation() {
        assertThat(Customer.unsaved("Ana", " ", "3001234567", "Calle 1").email()).isNull();
        assertThatThrownBy(() -> Customer.unsaved("", null, "3001234567", "Calle 1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Customer.unsaved("Ana", null, "3001234567", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a new customer needs a ten-digit mobile starting with 3, stored as digits only")
    void customerPhoneRule() {
        assertThat(Customer.unsaved("Ana", null, "300 123-4567", "Calle 1").phone())
                .isEqualTo("3001234567");

        for (String bad : new String[] {"+57 300 123 4567", "6012345678", "2001234567", "300123", "abc", " "}) {
            assertThatThrownBy(() -> Customer.unsaved("Ana", null, bad, "Calle 1"))
                    .as(bad)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
