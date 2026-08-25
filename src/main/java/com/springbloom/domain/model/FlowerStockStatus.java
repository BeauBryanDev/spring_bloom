package com.springbloom.domain.model;

/**
 * Mirrors the flower_stock_status Postgres enum. Constant names must stay
 * identical to the database values. The label is customer facing Spanish.
 */
public enum FlowerStockStatus {

    IN_STOCK("En stock", true),
    INCOMING_RESTOCK("Reabastecimiento en camino", true),
    IMPORT_ON_REQUEST("Importación desde Países Bajos", true),
    NOT_FOR_SALE("No disponible para la venta", false);

    private final String label;
    private final boolean sellable;

    FlowerStockStatus(String label, boolean sellable) {
        this.label = label;
        this.sellable = sellable;
    }

    public String label() {
        return label;
    }

    public boolean sellable() {
        return sellable;
    }
}
