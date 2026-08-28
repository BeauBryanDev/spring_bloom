package com.springbloom.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A flower as the store knows it. Identity and botanical facts only: price and
 * availability hang off FlowerStock, so nothing here can drift from the catalog.
 */
@Getter
@RequiredArgsConstructor
public class FlowerSpecies {

    private final Long id;
    private final String speciesKey;
    private final String commonName;
    private final String scientificName;
    private final String originCountry;
    private final String description;
    private final String symbolicMeaning;

    /** Identity only, as loaded from flowers.json: no id and no botanical detail. */
    public static FlowerSpecies catalogEntry(
            String speciesKey, String commonName, String scientificName) {

        return new FlowerSpecies(null, speciesKey, commonName, scientificName, null, null, null);
    }

    /** False for a catalog entry, which has no row behind it yet. */
    public boolean persisted() {
        return id != null;
    }

    /**
     * Identity is the database id, never the fields. Two catalog entries both have
     * a null id and are deliberately not equal: nothing identifies them yet.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FlowerSpecies species)) {
            return false;
        }
        return id != null && id.equals(species.id);
    }

    @Override
    public int hashCode() {
        return FlowerSpecies.class.hashCode();
    }

    @Override
    public String toString() {
        return "FlowerSpecies[" + speciesKey + ", id=" + id + "]";
    }
}
