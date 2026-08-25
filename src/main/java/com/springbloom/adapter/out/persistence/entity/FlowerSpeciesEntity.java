package com.springbloom.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "flower_species")
@Getter
@Setter
@NoArgsConstructor
public class FlowerSpeciesEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "species_id")
    private Long id;

    @Column(name = "species_key", nullable = false, unique = true)
    private String speciesKey;

    @Column(name = "common_name", nullable = false)
    private String commonName;

    @Column(name = "scientific_name")
    private String scientificName;

    @Column(name = "origin_country")
    private String originCountry;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "symbolic_meaning")
    private String symbolicMeaning;
}