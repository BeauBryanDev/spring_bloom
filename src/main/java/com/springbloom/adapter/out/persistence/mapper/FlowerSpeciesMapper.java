package com.springbloom.adapter.out.persistence.mapper;

import com.springbloom.adapter.out.persistence.entity.FlowerSpeciesEntity;
import com.springbloom.domain.model.FlowerSpecies;

public class FlowerSpeciesMapper {

    private FlowerSpeciesMapper() {
    }

    public static FlowerSpecies toDomain(FlowerSpeciesEntity entity) {
        return new FlowerSpecies(
                entity.getId(),
                entity.getSpeciesKey(),
                entity.getCommonName(),
                entity.getScientificName(),
                entity.getOriginCountry(),
                entity.getDescription(),
                entity.getSymbolicMeaning()
        );
    }
}