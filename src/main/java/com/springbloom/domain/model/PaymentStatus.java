package com.springbloom.domain.model;

/** Mirrors payment_status in V2__complaints_and_payments.sql. */
public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    CANCELLED,
    REFUNDED,
    EXPIRED
}
