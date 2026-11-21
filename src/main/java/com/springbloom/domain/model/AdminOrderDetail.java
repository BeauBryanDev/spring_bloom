package com.springbloom.domain.model;

import java.util.List;
import java.util.Optional;

/** Everything the admin sees about one order, assembled read-only. */
public record AdminOrderDetail(
        FlowerOrder order,
        Customer customer,
        Optional<Shipment> shipment,
        List<Payment> payments) {

    /** Money was taken for an order that never confirmed: a human decides the refund. */
    public boolean needsRefund() {
        return order.status() == OrderStatus.PENDING
                && payments.stream().anyMatch(Payment::needsFollowUp);
    }

    public Optional<Payment> latestPayment() {
        return payments.stream().findFirst();
    }
}
