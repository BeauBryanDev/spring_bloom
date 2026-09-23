package com.springbloom.domain.service;

import lombok.Getter;

/**
 * A species the customer asked for cannot be sold in the amount requested.
 * A business outcome rather than a fault: the agent is expected to catch this
 * and offer an alternative, so the reason is customer facing Spanish.
 */
@Getter
public class UnavailableSpeciesException extends RuntimeException {

    private final String speciesKey;
    private final String reason;

    public UnavailableSpeciesException(String speciesKey, String reason) {
        super(speciesKey + ": " + reason);
        this.speciesKey = speciesKey;
        this.reason = reason;
    }
}
