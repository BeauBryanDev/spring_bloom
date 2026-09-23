package com.springbloom.domain.model;

/** Mirrors order_status in V1__init_schema.sql. Read-only until ConfirmOrderUseCase exists. */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PREPARING,
    READY_FOR_DISPATCH,
    DISPATCHED,
    DELIVERED,
    CANCELLED
}
