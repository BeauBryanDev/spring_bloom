package com.springbloom.domain.model;

import java.util.List;

import com.springbloom.domain.model.vo.Money;

/**
 * A garland or crown sold at a fixed price. The ingredients say which flowers it
 * is made of and nothing more: no stem counts, and no stock comes down for them.
 */
public record Creation(
        Long id,
        String creationKey,
        String name,
        ProductType productType,
        Money price,
        String imageFile,
        String description,
        int displayOrder,
        boolean active,
        List<Ingredient> ingredients) {

    public Creation {
        if (creationKey == null || creationKey.isBlank()) {
            throw new IllegalArgumentException("creationKey is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (productType == null || !productType.arrangement()) {
            throw new IllegalArgumentException("an arrangement is a GARLAND or a CROWN: " + productType);
        }
        if (price == null || price.amount().signum() <= 0) {
            throw new IllegalArgumentException("an arrangement needs a positive price");
        }
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
    }

    public record Ingredient(String speciesKey, String commonName) {
    }
}
