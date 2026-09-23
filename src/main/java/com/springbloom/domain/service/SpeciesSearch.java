package com.springbloom.domain.service;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.springbloom.domain.model.FlowerSpecies;

/**
 * Matches what a customer typed against the catalog. A customer writes "rosas",
 * not "rose": species_key is the machine vocabulary and must never be what a
 * person is required to guess.
 *
 * Pure and static - no state, so no bean and no Spring.
 */
public class SpeciesSearch {

    private static final int EXACT_COMMON_NAME = 100;
    private static final int EXACT_KEY = 95;
    private static final int COMMON_NAME_PREFIX = 80;
    private static final int COMMON_NAME_CONTAINS = 60;
    private static final int SCIENTIFIC_NAME_CONTAINS = 40;
    private static final int KEY_CONTAINS = 30;

    private SpeciesSearch() {
    }

    /** One candidate and why it matched, best score first. */
    public record Match(FlowerSpecies species, int score) {
    }

    /**
     * Best matches for a free-text term, or empty when nothing resembles it.
     * Finding nothing is a normal answer the agent must relay, not a failure.
     */
    public static List<Match> match(Collection<FlowerSpecies> catalog, String term, int limit) {

        if (catalog == null || catalog.isEmpty()) {
            return List.of();
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }

        String needle = stem(term);

        if (needle.isBlank()) {

            return List.of();
        }

        return catalog.stream()
                .map(species -> new Match(species, score(species, needle)))
                .filter(match -> match.score() > 0)
                .sorted(Comparator.comparingInt(Match::score).reversed()
                        .thenComparing(match -> match.species().getCommonName()))
                .limit(limit)
                .toList();
    }

    /** The single best match, when a caller wants one answer rather than a list. */
    public static Optional<FlowerSpecies> best(Collection<FlowerSpecies> catalog, String term) {
        return match(catalog, term, 1).stream().map(Match::species).findFirst();
    }

    private static int score(FlowerSpecies species, String needle) {
        
        String commonName = stem(species.getCommonName());
        String scientificName = stem(species.getScientificName());
        String key = stem(species.getSpeciesKey());

        if (commonName.equals(needle)) {
            return EXACT_COMMON_NAME;
        }
        if (key.equals(needle)) {
            return EXACT_KEY;
        }
        if (commonName.startsWith(needle)) {
            return COMMON_NAME_PREFIX;
        }
        if (commonName.contains(needle)) {
            return COMMON_NAME_CONTAINS;
        }
        if (scientificName.contains(needle)) {
            return SCIENTIFIC_NAME_CONTAINS;
        }
        if (key.contains(needle)) {
            return KEY_CONTAINS;
        }
        return 0;
    }

    /**
     * Folds away everything a customer should not have to get right: accents,
     * case, the underscores and hyphens in a species_key, and the Spanish
     * plural. Applied to both sides, so "Peonías" finds "peonia".
     */
    static String stem(String text) {

        if (text == null) {
            return "";
        }

        String folded = Normalizer.normalize(text.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[_-]", " ")
                .replaceAll("\\s+", " ");

        return singular(folded);
    }

    /**
     * Enough Spanish plural to cover "rosas" and "claveles". Deliberately crude:
     * a real stemmer would be a dependency, and a wrong stem here only costs a
     * missed match, which the agent can recover from by asking again.
     */
    private static String singular(String word) {

        if (word.endsWith("es") && word.length() > 4) {

            return word.substring(0, word.length() - 2);
        }
        if (word.endsWith("s") && word.length() > 3) {

            return word.substring(0, word.length() - 1);
        }
        return word;
    }
}
