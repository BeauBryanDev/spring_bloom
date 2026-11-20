package com.springbloom.domain.port.in;

import com.springbloom.domain.model.Shipment;

/**
 * The shop's two real-world events for an order: it left the warehouse, and it
 * reached the customer. Admin only; the chat can never move an order.
 */
public interface UpdateShipmentUseCase {

    /**
     * Creates the shipment from the customer's name and address and moves the
     * order to DISPATCHED. Only a paid order (CONFIRMED, PREPARING or
     * READY_FOR_DISPATCH) can leave.
     *
     * @throws IllegalArgumentException with a Spanish reason when the order is unknown or not ready
     */
    Shipment dispatch(String orderNumber);

    /**
     * Stamps the delivery time and moves the order to DELIVERED. That time is what
     * the 24-hour claim window counts from. Only a DISPATCHED order can arrive.
     *
     * @throws IllegalArgumentException with a Spanish reason when the order is unknown or not dispatched
     */
    Shipment deliver(String orderNumber);
}
