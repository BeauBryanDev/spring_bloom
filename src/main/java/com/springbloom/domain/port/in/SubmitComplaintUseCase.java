package com.springbloom.domain.port.in;

import com.springbloom.domain.model.Complaint;
import com.springbloom.domain.model.ComplaintType;

/**
 * Files a claim against an order whose owner was already verified in this
 * conversation (see LookupOrderUseCase). Recording is all it does: the shop
 * decides the outcome, never the chat.
 */
public interface SubmitComplaintUseCase {

    SubmitResult submit(SubmitComplaintCommand command);
/*
A customer may have several orders, so the tool
    has to say which one. This stays safe because the service checks that the
    order belongs to the customer attached to this conversation. The model can't
    name someone else's order
*/
    record SubmitComplaintCommand(
                String sessionKey, 
                String orderNumber, 
                ComplaintType type, 
                String description) {

        public SubmitComplaintCommand {
            if (sessionKey == null || sessionKey.isBlank()) {
                throw new IllegalArgumentException("A session key is required");
            }
            if (orderNumber == null || orderNumber.isBlank()) {
                throw new IllegalArgumentException("El numero de pedido es obligatorio");
            }
            if (type == null) {
                throw new IllegalArgumentException("A complaint type is required");
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("A complaint needs a description");
            }
            orderNumber = orderNumber.trim();
        }
    }

    /** Why a claim was not filed. The wording is the customer-facing reason, as with FlowerStockStatus. */
    enum Refusal {
        NOT_VERIFIED("Primero hay que verificar el pedido con su numero y el telefono del pedido"),
        NOT_DELIVERED_YET("El pedido todavia no figura como entregado, asi que solo se puede "
                + "registrar que no llego o que llego tarde");

        private final String reason;

        Refusal(String reason) {
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }

    /**
     * Exactly one of complaint or refusal is set. alreadyOpen marks a claim of the
     * same type that was still open on that order: it is returned, not duplicated.
     */
    record SubmitResult(Complaint complaint, 
                        boolean alreadyOpen, 
                        Refusal refusal) {

        public SubmitResult {
            if ((complaint == null) == (refusal == null)) {
                throw new IllegalArgumentException("A result is either a claim or a refusal");
            }
        }

        public static SubmitResult filed(Complaint complaint) {
            return new SubmitResult(complaint, false, null);
        }

        public static SubmitResult alreadyOpen(Complaint existing) {
            return new SubmitResult(existing, true, null);
        }

        public static SubmitResult refused(Refusal refusal) {
            return new SubmitResult(null, false, refusal);
        }
    }
}
