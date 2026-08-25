package com.springbloom.domain.port.out;

import com.springbloom.domain.model.FlowerSpecies;
import java.util.List;
import java.util.Optional;

public interface FlowerSpeciesRepository {

    List<FlowerSpecies> findAll();

    /** Looks up the persisted species by species_key, the label the model emits. */
    Optional<FlowerSpecies> findBySpeciesKey(String speciesKey);
}
