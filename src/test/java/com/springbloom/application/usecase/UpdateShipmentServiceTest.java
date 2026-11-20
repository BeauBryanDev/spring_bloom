package com.springbloom.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.springbloom.domain.model.Customer;
import com.springbloom.domain.model.FlowerOrder;
import com.springbloom.domain.model.OrderItem;
import com.springbloom.domain.model.OrderItemSpecies;
import com.springbloom.domain.model.OrderStatus;
import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.Shipment;
import com.springbloom.domain.model.ShipmentStatus;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.out.CustomerRepository;
import com.springbloom.domain.port.out.FlowerOrderRepository;
import com.springbloom.domain.port.out.ShipmentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateShipmentServiceTest {

    private static final String ORDER = "ORD-20260801-12345";
    private static final Instant NOW = Instant.parse("2026-08-26T15:00:00Z");

    private FlowerOrderRepository orders;
    private ShipmentRepository shipments;
    private UpdateShipmentService service;

    @BeforeEach
    void setUp() {
        orders = mock(FlowerOrderRepository.class);
        shipments = mock(ShipmentRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        service = new UpdateShipmentService(orders, customers, shipments,
                Clock.fixed(NOW, ZoneOffset.UTC));

        when(customers.findById(7L)).thenReturn(Optional.of(
                new Customer(7L, "Ana Perez", null, "3001234567", "Calle 1 # 2-3")));
        when(shipments.findByOrderId(10L)).thenReturn(Optional.empty());
        when(shipments.save(any())).thenAnswer(call -> call.getArgument(0));
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

    @Test
    @DisplayName("an unpaid order cannot be dispatched")
    void refusesAnUnpaidOrder() {
        orderIs(OrderStatus.PENDING);

        assertThatThrownBy(() -> service.dispatch(ORDER)).isInstanceOf(IllegalArgumentException.class);
        verify(shipments, never()).save(any());
    }

    @Test
    @DisplayName("dispatching copies the customer's address and moves the order")
    void dispatchesAPaidOrder() {
        orderIs(OrderStatus.CONFIRMED);

        Shipment shipment = service.dispatch(ORDER);

        assertThat(shipment.status()).isEqualTo(ShipmentStatus.DISPATCHED);
        assertThat(shipment.deliveryAddress()).isEqualTo("Calle 1 # 2-3");
        verify(orders).updateStatus(10L, OrderStatus.DISPATCHED);
    }

    @Test
    @DisplayName("delivering stamps the time the 24-hour window counts from")
    void deliversADispatchedOrder() {
        orderIs(OrderStatus.DISPATCHED);
        when(shipments.findByOrderId(10L)).thenReturn(Optional.of(Shipment.dispatched(
                10L, "Ana Perez", "Calle 1 # 2-3", NOW.minusSeconds(3600))));

        service.deliver(ORDER);

        ArgumentCaptor<Shipment> saved = ArgumentCaptor.forClass(Shipment.class);
        verify(shipments).save(saved.capture());
        assertThat(saved.getValue().deliveredAt()).isEqualTo(NOW);
        verify(orders).updateStatus(10L, OrderStatus.DELIVERED);
    }
}
