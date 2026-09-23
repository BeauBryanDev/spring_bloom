package com.springbloom.domain.service;

import java.math.BigDecimal;
import java.util.List;

import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.Quotation;
import com.springbloom.domain.model.QuotationItem;
import com.springbloom.domain.model.QuotationItemSpecies;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.service.pricing.PricingStrategyFactory;

/**
 * Builds priced quotation lines from live species and stock. This is the only
 * place that turns a customer request into money, so availability and pricing
 * cannot drift apart.
 */
public class QuotationComposer {

    private final PricingStrategyFactory strategies;

    public QuotationComposer(PricingStrategyFactory strategies) {
        this.strategies = strategies;
    }

    /** One species and its live stock, with how many stems the customer wants. */
    public record Selection(FlowerSpecies species, FlowerStock stock, int quantity) {

        public Selection {
            if (species == null || stock == null) {
                throw new IllegalArgumentException("species and stock are required");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive: " + quantity);
            }
        }
    }

    /**
     * @param discountPercentage 0..100 for BOUQUET and GARLAND, null for INDIVIDUAL
     * @throws UnavailableSpeciesException if any species cannot be sold in the amount asked
     */
    public QuotationItem composeItem(
            ProductType productType, BigDecimal discountPercentage, List<Selection> selections) {

        if (selections == null || selections.isEmpty()) {
            throw new IllegalArgumentException("A quotation line needs at least one species");
        }

        for (Selection selection : selections) {
            requireAvailable(selection);
        }

        List<QuotationItemSpecies> lines = selections.stream()
                .map(s -> QuotationItemSpecies.of(s.species(), s.stock(), s.quantity()))
                .toList();

        Money composedSubtotal = lines.stream()
                .map(QuotationItemSpecies::lineTotal)
                .reduce(Money.ZERO, Money::plus);

        Money subtotal = strategies.forType(productType)
                .calculatePrice(composedSubtotal, discountPercentage);

        return new QuotationItem(productType, discountPercentage, lines, composedSubtotal, subtotal);
    }

    public Quotation compose(List<QuotationItem> items) {
        return Quotation.of(items);
    }

    private void requireAvailable(Selection selection) {
        FlowerStock stock = selection.stock();
        String key = selection.species().getSpeciesKey();

        if (!stock.sellable()) {
            throw new UnavailableSpeciesException(key, stock.getStatus().label());
        }
        if (!stock.canFulfil(selection.quantity())) {
            throw new UnavailableSpeciesException(
                    key, "solo quedan " + stock.getQuantity() + " unidades");
        }
    }
}
