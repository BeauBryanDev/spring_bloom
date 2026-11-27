package com.springbloom.adapter.out.persistence.entity;

import java.io.Serializable;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One flower an arrangement is made of. No quantity column, by decision. */
@Entity
@Table(name = "creation_species")
@IdClass(CreationSpeciesEntity.Key.class)
@Immutable
@Getter
@NoArgsConstructor
public class CreationSpeciesEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creation_id", nullable = false)
    private CreationEntity creation;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "species_id", nullable = false)
    private FlowerSpeciesEntity species;

    @Column(name = "position", nullable = false)
    private short position;

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private Long creation;
        private Long species;
    }
}
