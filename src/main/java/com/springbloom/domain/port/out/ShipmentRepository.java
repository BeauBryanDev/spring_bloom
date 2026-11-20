package com.springbloom.domain.port.out;

import java.util.Optional;

import com.springbloom.domain.model.Shipment;

public interface ShipmentRepository {

    /** At most one: shipment.order_id is UNIQUE. */
    Optional<Shipment> findByOrderId(Long orderId);

    /** Inserts when the id is null, otherwise updates in place. */
    Shipment save(Shipment shipment);
}
