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

    /**
     * NOT_MATCHED covers both an unknown order and a wrong phone, on purpose. The
     * wording is the customer-facing reason, as with FlowerStockStatus.label().
     */
    enum Outcome {
        VERIFIED(""),
        NOT_MATCHED("No pude verificar ese pedido con esos datos. Revise el numero de pedido "
                + "y el telefono con el que se hizo"),
        INVALID_PHONE("El telefono debe ser un celular colombiano de 10 digitos que empiece por 3"),
        LOCKED("Por seguridad no puedo seguir verificando pedidos en esta conversacion por un "
                + "rato. El equipo humano puede ayudarle desde la pagina de contacto");

        private final String reason;

        Outcome(String reason) {
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
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
