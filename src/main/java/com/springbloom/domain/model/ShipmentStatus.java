package com.springbloom.domain.model;

/** Mirrors the shipment_status Postgres enum. Constants match the DB values exactly. */
public enum ShipmentStatus {
    PENDING,
    DISPATCHED,
    DELIVERED,
    CANCELLED
}
