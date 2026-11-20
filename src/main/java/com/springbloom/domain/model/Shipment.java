package com.springbloom.domain.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The delivery of one order. deliveredAt is what the 24-hour claim window is
 * counted from, so a DELIVERED shipment must carry it.
 */
public record Shipment(
        Long id,
        Long orderId,
        ShipmentStatus status,
        String recipientName,
        String deliveryAddress,
        String trackingCode,
        LocalDate scheduledDate,
        Instant dispatchedAt,
        Instant deliveredAt) {

    public Shipment {
        if (orderId == null) {
            throw new IllegalArgumentException("A shipment belongs to an order");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (recipientName == null || recipientName.isBlank()) {
            throw new IllegalArgumentException("recipientName is required");
        }
        if (deliveryAddress == null || deliveryAddress.isBlank()) {
            throw new IllegalArgumentException("deliveryAddress is required");
        }
        if (status == ShipmentStatus.DELIVERED && deliveredAt == null) {
            throw new IllegalArgumentException("A DELIVERED shipment needs a delivery date");
        }
    }

    public static Shipment dispatched(Long orderId, 
                                        String recipient, 
                                        String address,
                                        Instant when) {

        return new Shipment(null, orderId,
                            ShipmentStatus.DISPATCHED, 
                            recipient, 
                            address,
                            null, null,
                            when, null);
                }

    public boolean delivered() {

        return status == ShipmentStatus.DELIVERED;
    }

    public Shipment deliveredAt(Instant when) {

        return new Shipment(id, orderId,
                            ShipmentStatus.DELIVERED, 
                            recipientName, 
                            deliveryAddress,
                            trackingCode, 
                            scheduledDate, 
                            dispatchedAt,
                            when);
    }
    
}
