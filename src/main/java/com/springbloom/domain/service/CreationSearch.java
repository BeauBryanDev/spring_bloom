package com.springbloom.domain.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.springbloom.domain.model.Creation;
import com.springbloom.domain.model.ProductType;

/**
 * Finds an arrangement from what a customer types ("la corona de lujo",
 * "guirnalda clasica"). Static and pure, like SpeciesSearch. More than one match
 * means the customer must say which: "tropical" is both a garland and a crown.
 */
public final class CreationSearch {

    private static final int STEM = 5;

    private CreationSearch() {
    }

    public static List<Creation> search(String text, List<Creation> creations) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String folded = fold(text);
        List<String> tokens = Arrays.asList(folded.split(" "));
        ProductType type = typeIn(tokens);

        return creations.stream()
                .filter(c -> folded.equals(fold(c.creationKey())) || folded.equals(fold(c.name()))
                        || (distinctiveWordIn(c, tokens) && (type == null || type == c.productType())))
                .toList();
    }

    private static boolean distinctiveWordIn(Creation creation, List<String> tokens) {
        String[] words = fold(creation.name()).split(" ");
        String stem = stem(words[words.length - 1]);
        return tokens.stream().anyMatch(token -> token.startsWith(stem));
    }

    private static ProductType typeIn(List<String> tokens) {
        for (String token : tokens) {
            if (token.startsWith("corona")) {
                return ProductType.CROWN;
            }
            if (token.startsWith("garland") || token.startsWith("guirnald") || token.startsWith("girnald")) {
                return ProductType.GARLAND;
            }
        }
        return null;
    }

    private static String stem(String word) {
        return word.length() <= STEM ? word : word.substring(0, STEM);
    }

    static String fold(String text) {
        String stripped = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return stripped.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
