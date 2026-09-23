package com.springbloom.domain.model;

import java.time.Instant;

import com.springbloom.domain.model.vo.Money;

/**
 * Read-only order header for the admin view. No line items: the write side
 * (ConfirmOrderUseCase, order_item, stock decrement on confirmation) does not
 * exist yet, so this only shows what is already sitting in flower_order.
 */
public record FlowerOrderSummary(
        String orderNumber,
        String customerName,
        OrderStatus status,
        Money totalAmount,
        Instant createdAt) {
}
