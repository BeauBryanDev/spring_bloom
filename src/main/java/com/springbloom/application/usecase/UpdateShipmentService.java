package com.springbloom.application.usecase;

import java.time.Clock;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springbloom.domain.model.Customer;
import com.springbloom.domain.model.FlowerOrder;
import com.springbloom.domain.model.OrderStatus;
import com.springbloom.domain.model.Shipment;
import com.springbloom.domain.port.in.UpdateShipmentUseCase;
import com.springbloom.domain.port.out.CustomerRepository;
import com.springbloom.domain.port.out.FlowerOrderRepository;
import com.springbloom.domain.port.out.ShipmentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Transactional, unlike the chat services: the shipment row and the order status
 * must change together, or an order could read DISPATCHED with no shipment
 * behind it. Nothing here needs REQUIRES_NEW, so one outer transaction is right.
 */
@Service
@RequiredArgsConstructor
public class UpdateShipmentService implements UpdateShipmentUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateShipmentService.class);

    /** Paid orders: the only ones that may leave the warehouse. */
    private static final Set<OrderStatus> READY_TO_LEAVE = EnumSet.of(
                                                            OrderStatus.CONFIRMED, 
                                                            OrderStatus.PREPARING, 
                                                            OrderStatus.READY_FOR_DISPATCH);

    private final FlowerOrderRepository orders;
    private final CustomerRepository customers;
    private final ShipmentRepository shipments;
    private final Clock clock;

    @Override
    @Transactional
    public Shipment dispatch(String orderNumber) {
        FlowerOrder order = find(orderNumber);

        if (!READY_TO_LEAVE.contains(order.status())) {
            throw new IllegalArgumentException(
                    "Solo un pedido pagado puede despacharse. Estado actual: " + order.status());
        }
        if (shipments.findByOrderId(order.id()).isPresent()) {
            throw new IllegalArgumentException("Ese pedido ya tiene un envio registrado");
        }

        Customer customer = customers.findById(order.customerId()).orElseThrow(
                () -> new IllegalArgumentException("El pedido no tiene un cliente valido"));

        Shipment saved = shipments.save(Shipment.dispatched(
                order.id(), customer.fullName(), customer.deliveryAddress(), clock.instant()));
        orders.updateStatus(order.id(), OrderStatus.DISPATCHED);

        log.info("Order {} dispatched", order.orderNumber());
        return saved;
    }

    @Override
    @Transactional
    public Shipment deliver(String orderNumber) {
        FlowerOrder order = find(orderNumber);

        if (order.status() != OrderStatus.DISPATCHED) {
            throw new IllegalArgumentException(
                    "Solo un pedido despachado puede marcarse entregado. Estado actual: "
                            + order.status());
        }

        Shipment shipment = shipments.findByOrderId(order.id()).orElseThrow(
                () -> new IllegalArgumentException("Ese pedido no tiene un envio registrado"));

        Shipment saved = shipments.save(shipment.deliveredAt(clock.instant()));
        orders.updateStatus(order.id(), OrderStatus.DELIVERED);

        log.info("Order {} delivered", order.orderNumber());
        return saved;
    }

    private FlowerOrder find(String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new IllegalArgumentException("El numero de pedido es obligatorio");
        }
        return orders.findByNumber(orderNumber.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("No existe ese pedido"));
    }
}
