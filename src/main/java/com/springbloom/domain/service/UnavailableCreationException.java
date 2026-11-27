package com.springbloom.domain.service;

/** A garland or crown that is unknown or switched off, so it cannot be sold. */
public class UnavailableCreationException extends RuntimeException {

    private final String creationKey;

    public UnavailableCreationException(String creationKey) {
        super("Ese arreglo ya no esta disponible");
        this.creationKey = creationKey;
    }

    public String getCreationKey() {
        return creationKey;
    }
}
