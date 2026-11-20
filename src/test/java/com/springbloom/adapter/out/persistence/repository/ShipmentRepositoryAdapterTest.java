package com.springbloom.adapter.out.persistence.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.springbloom.domain.model.Customer;
import com.springbloom.domain.model.FlowerOrder;
import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.Quotation;
import com.springbloom.domain.model.Shipment;
import com.springbloom.domain.port.out.CustomerRepository;
import com.springbloom.domain.port.out.FlowerOrderRepository;
import com.springbloom.domain.port.out.FlowerSpeciesRepository;
import com.springbloom.domain.port.out.FlowerStockRepository;
import com.springbloom.domain.port.out.QuotationRepository;
import com.springbloom.domain.port.out.ShipmentRepository;
import com.springbloom.domain.service.QuotationComposer;
import com.springbloom.domain.service.QuotationComposer.Selection;
import com.springbloom.domain.service.pricing.PricingStrategyFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A real write: reads and ddl-auto: validate pass on a broken shipment_status
 * binding, so the status is asserted through SQL, not back through the mapper.
 *
 *   set -a; . ./.env; set +a; ./mvnw test
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_USER", matches = ".+")
class ShipmentRepositoryAdapterTest {

    @Autowired
    private ShipmentRepository shipments;
    @Autowired
    private FlowerOrderRepository orders;
    @Autowired
    private QuotationRepository quotations;
    @Autowired
    private CustomerRepository customers;
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

    /** The shipment cascades from its order; then quotation, then customer. */
    @AfterEach
    void removeWhatThisTestWrote() {
        orderIds.forEach(id -> jdbc.update("DELETE FROM flower_order WHERE id = ?", id));
        quotationIds.forEach(id -> jdbc.update("DELETE FROM quotation WHERE id = ?", id));
        customerIds.forEach(id -> jdbc.update("DELETE FROM customer WHERE id = ?", id));
    }

    @Test
    @DisplayName("a shipment is inserted, then updated in place to DELIVERED")
    void writesAndUpdatesAShipment() {
        FlowerSpecies rose = species.findBySpeciesKey("rose").orElseThrow();
        FlowerStock live = stock.findBySpeciesId(rose.getId()).orElseThrow();
        QuotationComposer composer = new QuotationComposer(strategies);
        Quotation quotation = quotations.save(composer.compose(List.of(
                composer.composeItem(ProductType.INDIVIDUAL, null, List.of(new Selection(rose, live, 3))))));
        quotationIds.add(quotation.id());
        Customer customer = customers.save(
                Customer.unsaved("Test Cliente", null, "3000000000", "Calle 1 # 2-3"));
        customerIds.add(customer.id());
        FlowerOrder order = orders.save(FlowerOrder.fromQuotation(quotation, customer.id()));
        orderIds.add(order.id());

        Shipment dispatched = shipments.save(Shipment.dispatched(
                order.id(), "Test Cliente", "Calle 1 # 2-3", Instant.now()));

        assertThat(jdbc.queryForObject(
                "SELECT status::text FROM shipment WHERE id = ?", String.class, dispatched.id()))
                .isEqualTo("DISPATCHED");

        shipments.save(dispatched.deliveredAt(Instant.now()));

        assertThat(jdbc.queryForObject(
                "SELECT status::text FROM shipment WHERE id = ?", String.class, dispatched.id()))
                .isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject(
                "SELECT delivered_at IS NOT NULL AND created_at IS NOT NULL FROM shipment WHERE id = ?",
                Boolean.class, dispatched.id())).isTrue();
        assertThat(shipments.findByOrderId(order.id())).isPresent();
    }
}
