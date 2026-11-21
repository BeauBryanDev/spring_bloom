package com.springbloom.application.usecase;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.springbloom.domain.model.AdminOrderDetail;
import com.springbloom.domain.model.Customer;
import com.springbloom.domain.model.FlowerOrder;
import com.springbloom.domain.model.OrderItem;
import com.springbloom.domain.model.OrderItemSpecies;
import com.springbloom.domain.model.OrderStatus;
import com.springbloom.domain.model.Payment;
import com.springbloom.domain.model.PaymentStatus;
import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.out.CustomerRepository;
import com.springbloom.domain.port.out.FlowerOrderRepository;
import com.springbloom.domain.port.out.PaymentRepository;
import com.springbloom.domain.port.out.ShipmentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminOrderDetailServiceTest {

    private static final String ORDER = "ORD-20260801-12345";
    private static final Instant NOW = Instant.parse("2026-08-26T15:00:00Z");

    private FlowerOrderRepository orders;
    private CustomerRepository customers;
    private ShipmentRepository shipments;
    private PaymentRepository payments;
    private AdminOrderDetailService service;

    @BeforeEach
    void setUp() {
        orders = mock(FlowerOrderRepository.class);
        customers = mock(CustomerRepository.class);
        shipments = mock(ShipmentRepository.class);
        payments = mock(PaymentRepository.class);
        service = new AdminOrderDetailService(orders, customers, shipments, payments);

        when(customers.findById(7L)).thenReturn(Optional.of(
                new Customer(7L, "Ana Perez", null, "3001234567", "Calle 1 # 2-3")));
        when(shipments.findByOrderId(10L)).thenReturn(Optional.empty());
        when(payments.findByOrderId(10L)).thenReturn(List.of());
    }

    private void orderIs(OrderStatus status) {
        OrderItemSpecies line = new OrderItemSpecies(
                1L, "Rosa", 3, Money.of("4000"), Money.of("12000"));
        OrderItem item = new OrderItem(
                ProductType.INDIVIDUAL, null, List.of(line), Money.of("12000"));
        when(orders.findByNumber(ORDER)).thenReturn(Optional.of(new FlowerOrder(
                10L, ORDER, 5L, 7L, status, List.of(item),
                Money.of("12000"), Money.of("0"), Money.of("12000"), NOW)));
    }

    private static Payment paid(String note) {
        return new Payment(1L, "PAY-1", 10L, "cs_1", "pi_1", PaymentStatus.PAID,
                Money.of("12000"), "COP", note, NOW);
    }

    @Test
    @DisplayName("assembles the order with its customer, and tolerates no shipment or payments")
    void assembles() {
        orderIs(OrderStatus.CONFIRMED);

        AdminOrderDetail detail = service.findByNumber(" " + ORDER.toLowerCase() + " ").orElseThrow();

        assertThat(detail.order().orderNumber()).isEqualTo(ORDER);
        assertThat(detail.customer().fullName()).isEqualTo("Ana Perez");
        assertThat(detail.shipment()).isEmpty();
        assertThat(detail.payments()).isEmpty();
        assertThat(detail.needsRefund()).isFalse();
    }

    @Test
    @DisplayName("an unknown or blank number is empty, not an error")
    void unknown() {
        when(orders.findByNumber("ORD-NOPE")).thenReturn(Optional.empty());

        assertThat(service.findByNumber("ORD-NOPE")).isEmpty();
        assertThat(service.findByNumber("  ")).isEmpty();
        assertThat(service.findByNumber(null)).isEmpty();
    }

    @Test
    @DisplayName("paid with a note on an order that stayed PENDING needs a refund")
    void shortStockNeedsRefund() {
        orderIs(OrderStatus.PENDING);
        when(payments.findByOrderId(10L)).thenReturn(List.of(paid("Pagado sin stock suficiente")));

        assertThat(service.findByNumber(ORDER).orElseThrow().needsRefund()).isTrue();
    }

    @Test
    @DisplayName("a normally paid, confirmed order does not")
    void normalPaymentDoesNot() {
        orderIs(OrderStatus.CONFIRMED);
        when(payments.findByOrderId(10L)).thenReturn(List.of(paid(null)));

        assertThat(service.findByNumber(ORDER).orElseThrow().needsRefund()).isFalse();
    }

    @Test
    @DisplayName("a pending order with an unpaid attempt does not")
    void unpaidAttemptDoesNot() {
        orderIs(OrderStatus.PENDING);
        when(payments.findByOrderId(10L)).thenReturn(List.of(new Payment(
                2L, "PAY-2", 10L, "cs_2", null, PaymentStatus.PENDING,
                Money.of("12000"), "COP", null, null)));

        assertThat(service.findByNumber(ORDER).orElseThrow().needsRefund()).isFalse();
    }
}
