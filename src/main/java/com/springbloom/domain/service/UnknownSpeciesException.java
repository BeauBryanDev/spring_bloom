package com.springbloom.domain.service;

import lombok.Getter;

/**
 * A species_key that is not in the catalog at all. Distinct from
 * UnavailableSpeciesException on purpose: that one is a real flower the customer
 * cannot have right now, this one is a request that never made sense, so the
 * agent should correct the key rather than offer an alternative.
 */
@Getter
public class UnknownSpeciesException extends RuntimeException {

    private final String speciesKey;

    public UnknownSpeciesException(String speciesKey) {
        
        super("No such species: " + speciesKey);
        this.speciesKey = speciesKey;
    }
}
