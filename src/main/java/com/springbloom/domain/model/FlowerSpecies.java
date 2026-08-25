package com.springbloom.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

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
}