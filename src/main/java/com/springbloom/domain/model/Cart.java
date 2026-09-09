package com.springbloom.domain.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a customer has picked, before any of it is a document.
 *
 * It holds species keys and quantities and NOTHING ELSE - no price, no name, no
 * availability. Those are re-read from flower_stock every time the cart is
 * shown, so a cart left open while a flower changes price or goes out of stock
 * cannot quote yesterday's figure, and nothing a browser sends can influence
 * what a stem costs.
 */
public class Cart {

    /** A guard against a script hammering "add": a cart is not a stress test. */
    public static final int MAX_LINES = 40;

    /** Per line. The largest bouquet discount tier starts at 60 stems. */
    public static final int MAX_QUANTITY_PER_LINE = 500;

    /** Insertion ordered, so the page does not reshuffle when a quantity changes. */
    private final Map<String, Integer> quantitiesByKey = new LinkedHashMap<>();

    public void add(String speciesKey, int quantity) {
        String key = requireKey(speciesKey);
        requirePositive(quantity);

        if (!quantitiesByKey.containsKey(key) && quantitiesByKey.size() >= MAX_LINES) {
            throw new IllegalStateException("el carrito no admite mas de " + MAX_LINES + " flores");
        }
        quantitiesByKey.merge(key, quantity, (existing, added) ->
                Math.min(existing + added, MAX_QUANTITY_PER_LINE));
    }

    /** Setting a quantity of zero removes the line, which is what a customer means by it. */
    public void setQuantity(String speciesKey, int quantity) {
        String key = requireKey(speciesKey);
        if (quantity <= 0) {
            quantitiesByKey.remove(key);
            return;
        }
        if (!quantitiesByKey.containsKey(key) && quantitiesByKey.size() >= MAX_LINES) {
            throw new IllegalStateException("el carrito no admite mas de " + MAX_LINES + " flores");
        }
        quantitiesByKey.put(key, Math.min(quantity, MAX_QUANTITY_PER_LINE));
    }

    public void remove(String speciesKey) {
        quantitiesByKey.remove(requireKey(speciesKey));
    }

    public void clear() {
        quantitiesByKey.clear();
    }

    public boolean isEmpty() {
        return quantitiesByKey.isEmpty();
    }

    /** Distinct flowers, which is what the header badge counts. */
    public int lineCount() {
        return quantitiesByKey.size();
    }

    /** Every stem in the cart, which is what a bouquet discount is decided from. */
    public int totalStems() {
        return quantitiesByKey.values().stream().mapToInt(Integer::intValue).sum();
    }

    public List<CartLine> lines() {
        List<CartLine> lines = new ArrayList<>(quantitiesByKey.size());
        quantitiesByKey.forEach((key, quantity) -> lines.add(new CartLine(key, quantity)));
        return List.copyOf(lines);
    }

    private static String requireKey(String speciesKey) {
        if (speciesKey == null || speciesKey.isBlank()) {
            throw new IllegalArgumentException("speciesKey is required");
        }
        // Matching is exact and case sensitive everywhere else this key is used.
        return speciesKey.trim();
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
    }

    /** One picked flower. Deliberately carries no price. */
    public record CartLine(String speciesKey, int quantity) {
    }
}
