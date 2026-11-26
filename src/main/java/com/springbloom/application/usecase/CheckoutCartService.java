package com.springbloom.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.springbloom.domain.model.Customer;
import com.springbloom.domain.model.FlowerOrder;
import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.Quotation;
import com.springbloom.domain.model.QuotationItem;
import com.springbloom.domain.model.QuotationStatus;
import com.springbloom.domain.port.in.CheckoutCartUseCase;
import com.springbloom.domain.port.in.PayOrderUseCase;
import com.springbloom.domain.port.out.CustomerRepository;
import com.springbloom.domain.port.out.FlowerOrderRepository;
import com.springbloom.domain.port.out.FlowerSpeciesRepository;
import com.springbloom.domain.port.out.FlowerStockRepository;
import com.springbloom.domain.port.out.QuotationRepository;
import com.springbloom.domain.service.PaymentNotConfiguredException;
import com.springbloom.domain.service.QuotationComposer;
import com.springbloom.domain.service.QuotationComposer.Selection;
import com.springbloom.domain.service.UnavailableSpeciesException;
import com.springbloom.domain.service.UnknownSpeciesException;
import com.springbloom.domain.service.pricing.BouquetDiscountPolicy;

import lombok.RequiredArgsConstructor;

/**
 * The cart's own path to an order, without the chat. The cart is priced again
 * here from live stock through the same QuotationComposer the agent uses, so the
 * figure Stripe charges is a real COT- document, never what the page displayed.
 *
 * Not @Transactional for the same reason as PlaceOrderService: the order insert
 * runs in its own REQUIRES_NEW transaction for the number retry. Everything that
 * can be rejected is checked before the first write.
 */
@Service
@RequiredArgsConstructor
public class CheckoutCartService implements CheckoutCartUseCase {

    private static final Logger log = LoggerFactory.getLogger(CheckoutCartService.class);
    private static final Duration VALIDITY = Duration.ofDays(5);

    private final FlowerSpeciesRepository speciesRepository;
    private final FlowerStockRepository stockRepository;
    private final QuotationComposer composer;
    private final QuotationRepository quotations;
    private final CustomerRepository customers;
    private final FlowerOrderRepository orders;
    private final PayOrderUseCase payOrder;
    private final Clock storeClock;

    @Override
    public CartCheckout checkout(CheckoutCartCommand command) {

        List<Selection> selections = command.lines().stream().map(this::select).toList();
        int stems = selections.stream().mapToInt(Selection::quantity).sum();
        ProductType type = BouquetDiscountPolicy.lineTypeFor(selections.size(), stems);

        QuotationItem item = composer.composeItem(type, null, selections);
        Customer customer = Customer.unsaved(
                                command.fullName(), 
                                command.email(), 
                                command.phone(), 
                                command.deliveryAddress()
                            );

        Customer saved = customers.save(customer);
        Quotation quotation = quotations.save(composer.compose(List.of(item))
                .withCustomer(saved.id())
                .withStatus(QuotationStatus.ACCEPTED)
                .withValidUntil(expiry()));

        FlowerOrder order = orders.save(FlowerOrder.fromQuotation(quotation, saved.id()));
        log.info("Cart checkout placed {} from {} for {}",
                order.orderNumber(), quotation.quotationNumber(), order.totalAmount().amount());

        try {
            String url = payOrder.startCheckout(order.orderNumber()).url();
            return new CartCheckout(order.orderNumber(), quotation.quotationNumber(), url, null);

        } catch (PaymentNotConfiguredException | IllegalStateException unavailable) {
            // The order is real and stays PENDING: it can be paid later from the cart page.
            log.warn("Checkout for {} could not start: {}", 
            order.orderNumber(), unavailable.getMessage());

            return new CartCheckout(order.orderNumber(), 
                                    quotation.quotationNumber(), null,
                                    unavailable.getMessage());
        }
    }

    private Selection select(CartLine line) {

        FlowerSpecies species = speciesRepository.findBySpeciesKey(line.speciesKey())
                .orElseThrow(() -> new UnknownSpeciesException(line.speciesKey()));
        FlowerStock stock = stockRepository.findBySpeciesKey(line.speciesKey())
                .orElseThrow(() -> new UnavailableSpeciesException(
                        line.speciesKey(), "sin informacion de inventario"));

        return new Selection(species, stock, line.quantity());
    }

    private Instant expiry() {
        
        return storeClock.instant().plus(VALIDITY).truncatedTo(ChronoUnit.MICROS);
    }
}
