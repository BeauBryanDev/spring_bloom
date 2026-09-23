package com.springbloom.adapter.in.web;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Predicate;

import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.in.ListCatalogUseCase.CatalogEntry;

/**
 * How the catalog page narrows and orders the shelf. The choices are enums
 * rather than free text: a query parameter arrives from the browser, and an
 * unknown value falls back to the default instead of failing the page.
 *
 * Filtering happens here, on the entries the use case returned, because it is a
 * display concern - the listing itself stays "everything with a stock row".
 */
final class CatalogFilters {

    private CatalogFilters() {
    }

    /** Mirrors flower_stock_status, plus the "no filter" case the pills start on. */
    enum Availability {
        TODOS("Todos", null),
        IN_STOCK("Disponible ahora", "IN_STOCK"),
        INCOMING_RESTOCK("Proximo restock", "INCOMING_RESTOCK"),
        IMPORT_ON_REQUEST("Bajo pedido (importacion)", "IMPORT_ON_REQUEST"),
        NOT_FOR_SALE("No disponible", "NOT_FOR_SALE");

        private final String label;
        private final String status;

        Availability(String label, String status) {
            this.label = label;
            this.status = status;
        }

        public String getLabel() {
            return label;
        }

        Predicate<CatalogEntry> matches() {
            return status == null ? entry -> true : entry -> entry.availability().equals(status);
        }
    }

    /**
     * Per-stem bands, in COP. A flower with no price is not in any band but
     * TODOS: there is no number to compare, which is the point of it having none.
     */
    enum PriceBand {
        TODOS("Todos los precios", null, null),
        UNDER_5000("Menos de $5.000", null, new BigDecimal("5000")),
        FROM_5000_TO_10000("$5.000 - $10.000", new BigDecimal("5000"), new BigDecimal("10000")),
        OVER_10000("Mas de $10.000", new BigDecimal("10000"), null);

        private final String label;
        private final BigDecimal from;
        private final BigDecimal to;

        PriceBand(String label, BigDecimal from, BigDecimal to) {
            this.label = label;
            this.from = from;
            this.to = to;
        }

        public String getLabel() {
            return label;
        }

        Predicate<CatalogEntry> matches() {
            if (from == null && to == null) {
                return entry -> true;
            }
            return entry -> entry.unitPrice()
                    .map(Money::amount)
                    .filter(amount -> from == null || amount.compareTo(from) >= 0)
                    .filter(amount -> to == null || amount.compareTo(to) < 0)
                    .isPresent();
        }
    }

    /** A flower with no price sorts last either way: it is not part of the ranking. */
    enum SortOrder {
        NOMBRE_ASC("Nombre A-Z"),
        PRECIO_ASC("Precio: menor a mayor"),
        PRECIO_DESC("Precio: mayor a menor");

        private final String label;

        SortOrder(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        Comparator<CatalogEntry> comparator() {
            Comparator<CatalogEntry> byName =
                    Comparator.comparing(CatalogEntry::commonName, String.CASE_INSENSITIVE_ORDER);

            return switch (this) {
                case NOMBRE_ASC -> byName;
                case PRECIO_ASC -> unpricedLast(price()).thenComparing(byName);
                case PRECIO_DESC -> unpricedLast(price().reversed()).thenComparing(byName);
            };
        }

        private static Comparator<CatalogEntry> price() {
            return Comparator.comparing(
                    entry -> entry.unitPrice().map(Money::amount).orElse(BigDecimal.ZERO));
        }

        private static Comparator<CatalogEntry> unpricedLast(Comparator<CatalogEntry> then) {
            return Comparator.<CatalogEntry, Boolean>comparing(
                    entry -> entry.unitPrice().isEmpty()).thenComparing(then);
        }
    }

    /** An unknown or missing parameter is the default, never an error page. */
    static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElse(fallback);
    }
}
