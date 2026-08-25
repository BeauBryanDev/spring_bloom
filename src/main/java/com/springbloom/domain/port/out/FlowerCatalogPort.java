package com.springbloom.domain.port.out;

import com.springbloom.domain.model.FlowerSpecies;

import java.util.Collection;
import java.util.Optional;

/**
 * Translates the labels emitted by the vision model into known species.
 * A model label is the species_key, so lookups are by exact name, never by
 * position: the output index only means something against the ONNX names map.
 */
public interface FlowerCatalogPort {

    Optional<FlowerSpecies> find(String speciesKey);

    FlowerSpecies require(String speciesKey);

    /** Resolves a model output index through the names read from ONNX metadata. */
    Optional<FlowerSpecies> resolveByIndex(int classIndex, String[] modelNames);

    Collection<FlowerSpecies> all();

    int size();
}
