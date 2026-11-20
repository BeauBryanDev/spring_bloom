package com.springbloom.domain.port.in;

import java.time.Instant;
import java.util.List;

import com.springbloom.domain.model.OrderStatus;
import com.springbloom.domain.model.ShipmentStatus;
import com.springbloom.domain.model.vo.Money;

/**
 * Proves a chat visitor owns an order: the order number plus the phone on it.
 * A match also ties the conversation to that customer, which is what lets a
 * later complaint be accepted without asking for the phone again.
 */
public interface LookupOrderUseCase {

    OrderLookup lookup(LookupOrderCommand command);

    record LookupOrderCommand(String sessionKey, String orderNumber, String phone) {

        public LookupOrderCommand {
            if (sessionKey == null || sessionKey.isBlank()) {
                throw new IllegalArgumentException("A session key is required");
            }
            if (orderNumber == null || orderNumber.isBlank()) {
                throw new IllegalArgumentException("El numero de pedido es obligatorio");
            }
            orderNumber = orderNumber.trim();
        }
    }

    /** NOT_MATCHED covers both an unknown order and a wrong phone, on purpose. */
    enum Outcome {
        VERIFIED,
        NOT_MATCHED,
        INVALID_PHONE,
        LOCKED
    }

    record OrderLookup(Outcome outcome, VerifiedOrder order) {

        public static OrderLookup verified(VerifiedOrder order) {
            return new OrderLookup(Outcome.VERIFIED, order);
        }

        public static OrderLookup refused(Outcome outcome) {
            if (outcome == Outcome.VERIFIED) {
                throw new IllegalArgumentException("A verified lookup needs its order");
            }
            return new OrderLookup(outcome, null);
        }

        public boolean isVerified() {
            return outcome == Outcome.VERIFIED;
        }
    }

    /**
     * Only what the customer needs to hear. shipmentStatus is null when no shipment
     * exists yet; deliveredAt and hoursSinceDelivery are null until it is delivered.
     */
    record VerifiedOrder(
            String orderNumber,
            OrderStatus status,
            Money totalAmount,
            Instant placedAt,
            ShipmentStatus shipmentStatus,
            Instant deliveredAt,
            Long hoursSinceDelivery,
            boolean withinReportWindow,
            List<String> items) {
    }
}
