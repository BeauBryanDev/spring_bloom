package com.springbloom.domain.service;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.service.SpeciesSearch.Match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Free-text matching, with the real common names from flowers.json. */
class SpeciesSearchTest {

    private static FlowerSpecies species(String key, String commonName, String scientificName) {
        return FlowerSpecies.catalogEntry(key, commonName, scientificName);
    }

    private static final List<FlowerSpecies> CATALOG = List.of(
            species("rose", "Rosa", "Rosa spp."),
            species("desert-rose", "Rosa del desierto", "Adenium obesum"),
            species("Carnation", "Clavel", "Dianthus caryophyllus"),
            species("peruvian_lily", "Astromelia", "Alstroemeria spp."),
            species("monkshood", "Aconito", "Aconitum napellus"),
            species("water_lily", "Nenufar", "Nymphaea spp."),
            species("hard-leaved_pocket_orchid", "Orquidea de bolsillo", "Paphiopedilum spp."));

    private static List<String> keys(List<Match> matches) {
        return matches.stream().map(match -> match.species().getSpeciesKey()).toList();
    }

    @Nested
    class WhatCustomersType {

        @Test
        @DisplayName("a Spanish plural finds the singular species")
        void matchesASpanishPlural() {
            assertThat(keys(SpeciesSearch.match(CATALOG, "rosas", 5)))
                    .containsExactly("rose", "desert-rose");
        }

        @Test
        @DisplayName("an -es plural is handled too")
        void matchesAnEsPlural() {
            assertThat(keys(SpeciesSearch.match(CATALOG, "claveles", 5)))
                    .containsExactly("Carnation");
        }

        @Test
        @DisplayName("accents and case are folded away on both sides")
        void ignoresAccentsAndCase() {
            assertThat(keys(SpeciesSearch.match(CATALOG, "ACÓNITO", 5)))
                    .containsExactly("monkshood");
            assertThat(keys(SpeciesSearch.match(CATALOG, "nenúfar", 5)))
                    .containsExactly("water_lily");
        }

        @Test
        @DisplayName("a species_key still works, so the agent can pass one straight through")
        void matchesTheMachineVocabulary() {
            assertThat(keys(SpeciesSearch.match(CATALOG, "peruvian_lily", 5)))
                    .containsExactly("peruvian_lily");
        }

        @Test
        @DisplayName("the separators inside a species_key are not something a customer must type")
        void foldsKeySeparators() {
            assertThat(keys(SpeciesSearch.match(CATALOG, "hard leaved pocket orchid", 5)))
                    .containsExactly("hard-leaved_pocket_orchid");
        }

        @Test
        @DisplayName("a scientific name matches, for the customer who knows one")
        void matchesAScientificName() {
            assertThat(keys(SpeciesSearch.match(CATALOG, "Alstroemeria", 5)))
                    .containsExactly("peruvian_lily");
        }
    }

    @Nested
    class Ranking {

        @Test
        @DisplayName("an exact common name outranks a flower that merely contains it")
        void prefersTheExactName() {
            List<Match> matches = SpeciesSearch.match(CATALOG, "rosa", 5);

            assertThat(keys(matches)).containsExactly("rose", "desert-rose");
            assertThat(matches.get(0).score()).isGreaterThan(matches.get(1).score());
        }

        @Test
        @DisplayName("the limit caps the alternatives offered")
        void respectsTheLimit() {
            assertThat(SpeciesSearch.match(CATALOG, "rosa", 1)).hasSize(1);
        }

        @Test
        @DisplayName("best() answers with one species or nothing")
        void picksASingleBest() {
            assertThat(SpeciesSearch.best(CATALOG, "rosas"))
                    .get().extracting(FlowerSpecies::getSpeciesKey).isEqualTo("rose");
            assertThat(SpeciesSearch.best(CATALOG, "tulipan")).isEmpty();
        }
    }

    @Nested
    class NothingFound {

        @Test
        @DisplayName("a flower we do not carry is an empty answer, not an exception")
        void returnsEmptyForAnUnknownFlower() {
            assertThat(SpeciesSearch.match(CATALOG, "girasol", 5)).isEmpty();
        }

        @Test
        @DisplayName("a blank term matches nothing rather than everything")
        void returnsEmptyForABlankTerm() {
            assertThat(SpeciesSearch.match(CATALOG, "   ", 5)).isEmpty();
            assertThat(SpeciesSearch.match(CATALOG, null, 5)).isEmpty();
        }

        @Test
        @DisplayName("an empty catalog is answerable, a bad limit is a programming error")
        void guardsItsInputs() {
            assertThat(SpeciesSearch.match(List.of(), "rosa", 5)).isEmpty();

            assertThatThrownBy(() -> SpeciesSearch.match(CATALOG, "rosa", 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
