package com.springbloom.adapter.out.persistence.mapper;

import com.springbloom.adapter.out.persistence.entity.ShipmentEntity;
import com.springbloom.domain.model.Shipment;

public final class ShipmentMapper {

    private ShipmentMapper() {
    }

    public static Shipment toDomain(ShipmentEntity entity) {

        return new Shipment(entity.getId(), 
                            entity.getOrderId(), 
                            entity.getStatus(),
                            entity.getRecipientName(), 
                            entity.getDeliveryAddress(), 
                            entity.getTrackingCode(),
                            entity.getScheduledDate(), 
                            entity.getDispatchedAt(), 
                            entity.getDeliveredAt());
    }

    /** Copies every mutable field onto the entity, new or loaded, so an update stays an UPDATE. */
    public static void copyInto(Shipment shipment, ShipmentEntity entity) {
        
        entity.setOrderId(shipment.orderId());
        entity.setStatus(shipment.status());
        entity.setRecipientName(shipment.recipientName());
        entity.setDeliveryAddress(shipment.deliveryAddress());
        entity.setTrackingCode(shipment.trackingCode());
        entity.setScheduledDate(shipment.scheduledDate());
        entity.setDispatchedAt(shipment.dispatchedAt());
        entity.setDeliveredAt(shipment.deliveredAt());
    }
}
