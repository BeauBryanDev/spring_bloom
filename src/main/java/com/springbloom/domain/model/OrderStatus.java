package com.springbloom.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Mirrors order_status in V1__init_schema.sql, and owns the one table of legal
 * moves between states. PREPARING and READY_FOR_DISPATCH exist in the database
 * and dispatch accepts them, but no code writes them yet.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PREPARING,
    READY_FOR_DISPATCH,
    DISPATCHED,
    DELIVERED,
    CANCELLED;

    public Set<OrderStatus> next() {
        return switch (this) {
            case PENDING -> EnumSet.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> EnumSet.of(PREPARING, DISPATCHED);
            case PREPARING -> EnumSet.of(READY_FOR_DISPATCH, DISPATCHED);
            case READY_FOR_DISPATCH -> EnumSet.of(DISPATCHED);
            case DISPATCHED -> EnumSet.of(DELIVERED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus target) {
        return next().contains(target);
    }

    public boolean terminal() {
        return next().isEmpty();
    }

    public boolean canDispatch() {
        return canTransitionTo(DISPATCHED);
    }

    public boolean canDeliver() {
        return canTransitionTo(DELIVERED);
    }

    public void requireTransitionTo(OrderStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalArgumentException(
                    "Un pedido en estado " + this + " no puede pasar a " + target);
        }
    }
}
