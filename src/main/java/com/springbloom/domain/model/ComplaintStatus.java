package com.springbloom.domain.model;

/** Mirrors the complaint_status Postgres enum. Constants match the DB values exactly. */
public enum ComplaintStatus {

    OPEN("Abierto"),
    IN_REVIEW("En revision"),
    RESOLVED("Resuelto"),
    REJECTED("No procede");

    private final String label;

    ComplaintStatus(String label) {

        this.label = label;
    }

    public String label() {

        return label;
    }

    /** Closed states carry a resolution and a date; the DB enforces the same pair. */
    public boolean closed() {
        
        return this == RESOLVED || this == REJECTED;
    }
}
