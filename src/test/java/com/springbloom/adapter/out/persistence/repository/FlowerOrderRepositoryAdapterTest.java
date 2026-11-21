package com.springbloom.adapter.out.persistence.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.springbloom.domain.model.Customer;
import com.springbloom.domain.model.FlowerOrder;
import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.OrderStatus;
import com.springbloom.domain.model.Payment;
import com.springbloom.domain.model.PaymentStatus;
import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.Quotation;
import com.springbloom.domain.model.QuotationStatus;
import com.springbloom.domain.port.out.CustomerRepository;
import com.springbloom.domain.port.out.FlowerOrderRepository;
import com.springbloom.domain.port.out.FlowerSpeciesRepository;
import com.springbloom.domain.port.out.FlowerStockRepository;
import com.springbloom.domain.port.out.PaymentRepository;
import com.springbloom.domain.port.out.QuotationRepository;
import com.springbloom.domain.service.QuotationComposer;
import com.springbloom.domain.service.QuotationComposer.Selection;
import com.springbloom.domain.service.pricing.PricingStrategyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real writes for the order half. Reads and ddl-auto: validate pass on a broken
 * enum binding, so every assertion on a status goes through SQL (status::text),
 * not back through the mapper that wrote it.
 *
 *   set -a; . ./.env; set +a; ./mvnw test
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_USER", matches = ".+")
class FlowerOrderRepositoryAdapterTest {

    @Autowired
    private FlowerOrderRepository orders;
    @Autowired
    private QuotationRepository quotations;
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private PaymentRepository payments;
    @Autowired
    private FlowerSpeciesRepository species;
    @Autowired
    private FlowerStockRepository stock;
    @Autowired
    private PricingStrategyFactory strategies;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> orderIds = new ArrayList<>();
    private final List<Long> quotationIds = new ArrayList<>();
    private final List<Long> customerIds = new ArrayList<>();

    /** Children first: order (cascades payments and lines), then quotation, then customer. */
    @AfterEach
    void removeWhatThisTestWrote() {
        orderIds.forEach(id -> jdbc.update("DELETE FROM flower_order WHERE id = ?", id));
        quotationIds.forEach(id -> jdbc.update("DELETE FROM quotation WHERE id = ?", id));
        customerIds.forEach(id -> jdbc.update("DELETE FROM customer WHERE id = ?", id));
        orderIds.clear();
        quotationIds.clear();
        customerIds.clear();
    }

    private Quotation savedQuotation() {
        FlowerSpecies rose = species.findBySpeciesKey("rose").orElseThrow();
        FlowerStock live = stock.findBySpeciesId(rose.getId()).orElseThrow();
        QuotationComposer composer = new QuotationComposer(strategies);

        Quotation saved = quotations.save(composer.compose(List.of(
                composer.composeItem(ProductType.INDIVIDUAL, null, List.of(new Selection(rose, live, 3))))));
        quotationIds.add(saved.id());
        return saved;
    }

    private FlowerOrder savedOrder(Quotation quotation) {
        Customer customer = customers.save(Customer.unsaved("Test Cliente", null, "3000000000", "Calle 1 # 2-3"));
        customerIds.add(customer.id());

        FlowerOrder order = orders.save(FlowerOrder.fromQuotation(quotation, customer.id()));
        orderIds.add(order.id());
        return order;
    }

    @Test
    @DisplayName("a real save writes the order, its lines and species, and reads back identical")
    void savesAndReadsBack() {
        Quotation quotation = savedQuotation();
        FlowerOrder saved = savedOrder(quotation);

        assertThat(saved.persisted()).isTrue();
        assertThat(saved.orderNumber()).matches("ORD-\\d{8}-\\d{5}");

        String status = jdbc.queryForObject(
                "SELECT status::text FROM flower_order WHERE id = ?", String.class, saved.id());
        assertThat(status).isEqualTo("PENDING");

        FlowerOrder read = orders.findByNumber(saved.orderNumber()).orElseThrow();
        assertThat(read.totalAmount()).isEqualTo(quotation.totalAmount());
        assertThat(read.items()).hasSize(1);
        assertThat(read.items().get(0).productType()).isEqualTo(ProductType.INDIVIDUAL);
        assertThat(read.items().get(0).species().get(0).commonNameSnapshot()).isEqualTo("Rosa");
        assertThat(read.stemsBySpecies().values()).containsExactly(3);
        assertThat(orders.findByQuotationId(quotation.id())).isPresent();
    }

    @Test
    @DisplayName("the admin summary joins the customer name and includes the new order")
    void summariesCarryCustomerName() {
        FlowerOrder saved = savedOrder(savedQuotation());

        var summary = orders.findAllSummaries().stream()
                .filter(row -> row.orderNumber().equals(saved.orderNumber()))
                .findFirst().orElseThrow();

        assertThat(summary.customerName()).isEqualTo("Test Cliente");
        assertThat(summary.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(summary.totalAmount()).isEqualTo(saved.totalAmount());
        assertThat(summary.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("a quotation converts to at most one order")
    void oneOrderPerQuotation() {
        Quotation quotation = savedQuotation();
        savedOrder(quotation);

        assertThatThrownBy(() -> orders.save(FlowerOrder.fromQuotation(quotation, customerIds.get(0))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("updateStatus writes the order_status enum")
    void updatesOrderStatus() {
        FlowerOrder saved = savedOrder(savedQuotation());

        orders.updateStatus(saved.id(), OrderStatus.CONFIRMED);

        assertThat(jdbc.queryForObject(
                "SELECT status::text FROM flower_order WHERE id = ?", String.class, saved.id()))
                .isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("a quotation's status can be moved to ACCEPTED without touching its lines")
    void updatesQuotationStatus() {
        Quotation quotation = savedQuotation();

        quotations.updateStatus(quotation.id(), QuotationStatus.ACCEPTED);

        assertThat(jdbc.queryForObject(
                "SELECT status::text FROM quotation WHERE id = ?", String.class, quotation.id()))
                .isEqualTo("ACCEPTED");
        assertThat(quotations.findById(quotation.id()).orElseThrow().items()).hasSize(1);
    }

    @Test
    @DisplayName("a payment binds payment_status, and paid_at is written with PAID")
    void paymentLifecycle() {
        FlowerOrder order = savedOrder(savedQuotation());
        String session = "cs_test_" + UUID.randomUUID();

        Payment pending = payments.save(Payment.pending(
                "PAY-" + UUID.randomUUID().toString().replace("-", ""), order.id(), session,
                order.totalAmount(), "COP"));

        assertThat(jdbc.queryForObject(
                "SELECT status::text FROM payment WHERE id = ?", String.class, pending.id()))
                .isEqualTo("PENDING");
        assertThat(payments.existsPaidForOrder(order.id())).isFalse();
        assertThat(payments.findBySessionId(session)).isPresent();

        Payment paid = payments.markPaid(pending.id(), "pi_test", null, Instant.now());

        assertThat(paid.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.paidAt()).isNotNull();
        assertThat(jdbc.queryForObject(
                "SELECT status::text FROM payment WHERE id = ?", String.class, pending.id()))
                .isEqualTo("PAID");
        assertThat(payments.existsPaidForOrder(order.id())).isTrue();
    }

    @Test
    @DisplayName("payments for an order come back newest first")
    void paymentsByOrder() {
        FlowerOrder order = savedOrder(savedQuotation());
        Payment first = payments.save(Payment.pending("PAY-" + UUID.randomUUID().toString().replace("-", ""),
                order.id(), "cs_a_" + UUID.randomUUID(), order.totalAmount(), "COP"));
        Payment second = payments.save(Payment.pending("PAY-" + UUID.randomUUID().toString().replace("-", ""),
                order.id(), "cs_b_" + UUID.randomUUID(), order.totalAmount(), "COP"));

        assertThat(payments.findByOrderId(order.id())).extracting(Payment::id)
                .containsExactly(second.id(), first.id());
    }

    @Test
    @DisplayName("an order is paid at most once, but failed attempts may repeat")
    void onlyOnePaidPaymentPerOrder() {
        FlowerOrder order = savedOrder(savedQuotation());

        Payment first = payments.save(Payment.pending("PAY-" + UUID.randomUUID().toString().replace("-", ""),
                order.id(), "cs_a_" + UUID.randomUUID(), order.totalAmount(), "COP"));
        Payment second = payments.save(Payment.pending("PAY-" + UUID.randomUUID().toString().replace("-", ""),
                order.id(), "cs_b_" + UUID.randomUUID(), order.totalAmount(), "COP"));

        payments.markPaid(first.id(), "pi_1", null, Instant.now());

        assertThatThrownBy(() -> payments.markPaid(second.id(), "pi_2", null, Instant.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the stock decrement is conditional: it succeeds, then refuses to go below zero")
    void atomicDecrement() {
        FlowerSpecies rose = species.findBySpeciesKey("rose").orElseThrow();
        Integer before = jdbc.queryForObject(
                "SELECT stock_quantity FROM flower_stock WHERE species_id = ?", Integer.class, rose.getId());
        assertThat(before).isNotNull().isGreaterThan(2);

        try {
            assertThat(stock.decrementIfAvailable(rose.getId(), 2)).isTrue();
            assertThat(jdbc.queryForObject(
                    "SELECT stock_quantity FROM flower_stock WHERE species_id = ?", Integer.class, rose.getId()))
                    .isEqualTo(before - 2);

            assertThat(stock.decrementIfAvailable(rose.getId(), before + 1000)).isFalse();
            assertThat(jdbc.queryForObject(
                    "SELECT stock_quantity FROM flower_stock WHERE species_id = ?", Integer.class, rose.getId()))
                    .isEqualTo(before - 2);
        } finally {
            jdbc.update("UPDATE flower_stock SET stock_quantity = ? WHERE species_id = ?", before, rose.getId());
        }
    }

    @Test
    @DisplayName("a species that is not IN_STOCK has no quantity to decrement")
    void refusesNonInStockSpecies() {
        Long importOnly = jdbc.queryForObject(
                "SELECT species_id FROM flower_stock WHERE status = 'IMPORT_ON_REQUEST' LIMIT 1", Long.class);

        assertThat(stock.decrementIfAvailable(importOnly, 1)).isFalse();
    }
}
