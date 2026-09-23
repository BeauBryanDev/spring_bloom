package com.springbloom.adapter.in.web;

import com.springbloom.domain.model.Cart;
import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.in.ListCatalogUseCase;
import com.springbloom.domain.port.in.ListCatalogUseCase.CatalogEntry;
import com.springbloom.domain.service.pricing.BouquetDiscountPolicy;
import com.springbloom.domain.service.pricing.PricingStrategyFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns the species keys in a cart into what the page shows.
 *
 * Every price here is read from flower_stock on this request. The cart stores no
 * money at all, so a cart left open overnight cannot show yesterday's price, and
 * nothing the browser sends can influence a total.
 *
 * The bouquet figure uses the same BouquetDiscountPolicy a real quotation uses,
 * so the cart, the catalog card and the document Florabelle raises can never
 * disagree. It is still an estimate of a bundle, not a quotation: only
 * RequestQuotationUseCase produces a COT- number.
 */
@Component
public class CartPricing {

    private final ListCatalogUseCase listCatalog;
    private final PricingStrategyFactory pricing;

    public CartPricing(ListCatalogUseCase listCatalog, PricingStrategyFactory pricing) {
        this.listCatalog = listCatalog;
        this.pricing = pricing;
    }

    public CartView price(Cart cart) {
        List<CartLineView> lines = new ArrayList<>();
        Money subtotal = Money.of(BigDecimal.ZERO);
        int sellableStems = 0;
        boolean anyUnavailable = false;

        for (Cart.CartLine line : cart.lines()) {
            Optional<CatalogEntry> found = listCatalog.findByKey(line.speciesKey());

            if (found.isEmpty()) {
                // The species vanished from the catalog while the cart was open.
                lines.add(CartLineView.gone(line.speciesKey(), line.quantity()));
                anyUnavailable = true;
                continue;
            }

            CatalogEntry entry = found.get();
            Optional<Money> unitPrice = entry.unitPrice();

            if (unitPrice.isEmpty()) {
                // NOT_FOR_SALE, or no stock row. The disabled button on the card is
                // a courtesy; this is the check that actually holds.
                lines.add(CartLineView.unsellable(entry, line.quantity()));
                anyUnavailable = true;
                continue;
            }

            Money lineTotal = unitPrice.get().multiply(line.quantity());
            subtotal = subtotal.plus(lineTotal);
            sellableStems += line.quantity();

            lines.add(CartLineView.sellable(entry, line.quantity(), unitPrice.get(), lineTotal,
                    entry.stock().canFulfil(line.quantity())));
        }

        BigDecimal discountPercentage = sellableStems > 0
                ? BouquetDiscountPolicy.discountFor(sellableStems)
                : BigDecimal.ZERO;

        Money bundleTotal = sellableStems > 0
                ? pricing.forType(ProductType.BOUQUET).calculatePrice(subtotal, discountPercentage)
                : Money.of(BigDecimal.ZERO);

        return new CartView(
                lines,
                subtotal,
                bundleTotal,
                subtotal.minus(bundleTotal),
                discountPercentage,
                sellableStems,
                cart.totalStems(),
                anyUnavailable);
    }

    /**
     * quotable is false exactly when the line has no price, so no template can
     * show a total for a flower the shop may not sell.
     */
    public record CartLineView(
            String speciesKey,
            String commonName,
            String scientificName,
            String imagePath,
            int quantity,
            boolean quotable,
            boolean enoughStock,
            String availability,
            String availabilityLabel,
            Money unitPrice,
            Money lineTotal) {

        static CartLineView sellable(CatalogEntry entry, int quantity, Money unitPrice,
                                     Money lineTotal, boolean enoughStock) {
            return new CartLineView(
                    entry.speciesKey(), entry.commonName(), entry.scientificName(),
                    entry.speciesKey() + ".jpg", quantity, true, enoughStock,
                    entry.availability(), entry.availabilityLabel(), unitPrice, lineTotal);
        }

        static CartLineView unsellable(CatalogEntry entry, int quantity) {
            return new CartLineView(
                    entry.speciesKey(), entry.commonName(), entry.scientificName(),
                    entry.speciesKey() + ".jpg", quantity, false, false,
                    entry.availability(), entry.availabilityLabel(), null, null);
        }

        static CartLineView gone(String speciesKey, int quantity) {
            return new CartLineView(
                    speciesKey, speciesKey, null, speciesKey + ".jpg", quantity,
                    false, false, "NOT_FOR_SALE", "Ya no esta en el catalogo", null, null);
        }
    }

    public record CartView(
            List<CartLineView> lines,
            Money subtotal,
            Money total,
            Money discountAmount,
            BigDecimal discountPercentage,
            int sellableStems,
            int totalStems,
            boolean anyUnavailable) {

        public boolean isEmpty() {
            return lines.isEmpty();
        }

        public boolean hasDiscount() {
            return discountPercentage.signum() > 0;
        }
    }
}
