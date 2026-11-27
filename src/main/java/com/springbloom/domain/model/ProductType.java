package com.springbloom.domain.model;

/** Mirrors the product_type enum (V1, plus CROWN from V6). */
public enum ProductType {
    INDIVIDUAL,
    BOUQUET,
    GARLAND,
    CROWN;

    /** A CROWN is only ever a fixed-price arrangement, never composed from stems. */
    public boolean pricedFromStems() {
        return this != CROWN;
    }

    public boolean arrangement() {
        return this == GARLAND || this == CROWN;
    }
}
