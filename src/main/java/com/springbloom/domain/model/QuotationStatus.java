package com.springbloom.domain.model;

/** Mirrors the quotation_status enum in V1__init_schema.sql. */
public enum QuotationStatus {
    DRAFT,
    SENT,
    ACCEPTED,
    EXPIRED,
    REJECTED;

    /** Only an accepted quotation may become an order. */
    public boolean convertibleToOrder() {
        return this == ACCEPTED;
    }
}
