package com.springbloom.domain.model;

import java.time.Instant;
import java.util.List;

import com.springbloom.domain.model.vo.Money;

/**
 * An order is a quotation the customer agreed to, frozen: the lines, snapshots
 * and totals are copied, never recomputed, so the figure on the order is the
 * figure the customer was quoted.
 */
public record FlowerOrder(
        Long id,
        String orderNumber,
        Long quotationId,
        Long customerId,
        OrderStatus status,
        List<OrderItem> items,
        Money subtotal,
        Money discountAmount,
        Money totalAmount,
        Instant createdAt) {

    public FlowerOrder {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one line");
        }
        if (quotationId == null || customerId == null) {
            throw new IllegalArgumentException("An order needs a quotation and a customer");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        items = List.copyOf(items);
    }

    /** Unsaved, PENDING: no number, no id. Paying it is what confirms it. */
    public static FlowerOrder fromQuotation(Quotation quotation, Long customerId) {
        return new FlowerOrder(null, null, quotation.id(),
                customerId,
                OrderStatus.PENDING,
                quotation.items().stream().map(OrderItem::from).toList(),
                quotation.subtotal(),
                quotation.discountAmount(),
                quotation.totalAmount(),
                null);
    }

    public boolean persisted() {
        return id != null;
    }

    public FlowerOrder withNumber(String number) {

        return new FlowerOrder(id, number,
                quotationId,
                customerId,
                status,
                items,
                subtotal,
                discountAmount,
                totalAmount,
                createdAt);
    }

    public FlowerOrder withId(Long assignedId) {

        return new FlowerOrder(assignedId,
                orderNumber,
                quotationId,
                customerId,
                status,
                items,
                subtotal,
                discountAmount,
                totalAmount,
                createdAt);
    }

    /**
     * Every (species, quantity) the order will take out of stock, summed per
     * species.
     */
    public java.util.Map<Long, Integer> stemsBySpecies() {
        java.util.Map<Long, Integer> totals = new java.util.LinkedHashMap<>();

        for (OrderItem item : items) {

            for (OrderItemSpecies line : item.species()) {

                totals.merge(line.speciesId(),
                        line.quantity(),
                        Integer::sum);
            }
        }
        return totals;
    }
}
