package com.springbloom.domain.model;

/** Mirrors the complaint_type Postgres enum. Constants match the DB values exactly. */
public enum ComplaintType {

    DAMAGED_FLOWERS("Flores en mal estado"),
    LATE_DELIVERY("Entrega tarde"),
    NOT_DELIVERED("Pedido no recibido"),
    WRONG_ITEM("Pedido equivocado"),
    OTHER("Otro motivo");

    private final String label;

    ComplaintType(String label) {
        this.label = label;
    }

    /** The Spanish wording shown to the customer, as with FlowerStockStatus. */
    public String label() {
        return label;
    }
}
