package com.springbloom.application.usecase;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.springbloom.domain.model.Conversation;
import com.springbloom.domain.model.Customer;
import com.springbloom.domain.model.FlowerOrder;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.OrderItemSpecies;
import com.springbloom.domain.model.Quotation;
import com.springbloom.domain.model.QuotationStatus;
import com.springbloom.domain.port.in.PlaceOrderUseCase;
import com.springbloom.domain.port.out.ConversationRepository;
import com.springbloom.domain.port.out.CustomerRepository;
import com.springbloom.domain.port.out.FlowerOrderRepository;
import com.springbloom.domain.port.out.FlowerStockRepository;
import com.springbloom.domain.port.out.QuotationRepository;
import com.springbloom.domain.service.UnavailableSpeciesException;

import lombok.RequiredArgsConstructor;

/**
 * Deliberately not @Transactional: the order insert runs in its own REQUIRES_NEW
 * transaction for the number retry, and it would not see a customer row still
 * uncommitted in an outer one. Each step commits by itself; the worst a failure
 * leaves behind is a customer row, never a half-written order.
 */
@Service
@RequiredArgsConstructor
public class PlaceOrderService implements PlaceOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlaceOrderService.class);

    /** One message for "unknown" and "not yours": a different one would confirm a number exists. */
    private static final String NOT_FOUND = "No encontramos esa cotizacion en esta conversacion";

    private final QuotationRepository quotations;
    private final FlowerOrderRepository orders;
    private final CustomerRepository customers;
    private final ConversationRepository conversations;
    private final FlowerStockRepository stock;
    private final Clock storeClock;

    @Override
    public FlowerOrder place(PlaceOrderCommand command) {

        Quotation quotation = quotations
                .findByNumber(command.quotationNumber().trim().toUpperCase(Locale.ROOT))
                .filter(found -> belongsTo(found, command.sessionKey()))
                .orElseThrow(() -> new IllegalArgumentException(NOT_FOUND));

        Optional<FlowerOrder> existing = orders.findByQuotationId(quotation.id());
        if (existing.isPresent()) {
            return existing.get();
        }

        requireOpen(quotation);
        requireStock(quotation);

        Customer customer = customers.save(Customer.unsaved(
                command.fullName(), command.email(), command.phone(), command.deliveryAddress()));

        FlowerOrder placed = orders.save(FlowerOrder.fromQuotation(quotation, customer.id()));
        quotations.updateStatus(quotation.id(), QuotationStatus.ACCEPTED);

        log.info("Session {} placed {} from {}",
                command.sessionKey(), placed.orderNumber(), quotation.quotationNumber());
        return placed;
    }

    /** A quotation is only ever this session's own: the key is the customer's whole identity. */
    private boolean belongsTo(Quotation quotation, String sessionKey) {
        UUID mine = conversations.findBySessionKey(sessionKey).map(Conversation::id).orElse(null);
        return mine != null && mine.equals(quotation.conversationId());
    }

    private void requireOpen(Quotation quotation) {
        if (quotation.status() == QuotationStatus.EXPIRED || quotation.status() == QuotationStatus.REJECTED) {
            throw new IllegalArgumentException("Esa cotizacion ya no esta vigente");
        }
        if (quotation.validUntil() != null && quotation.validUntil().isBefore(storeClock.instant())) {
            throw new IllegalArgumentException("Esa cotizacion ya vencio, hay que preparar una nueva");
        }
    }

    /**
     * Advice only: stock is not held, and the real check is the atomic decrement
     * when the payment lands. This just avoids selling what is already gone.
     */
    private void requireStock(Quotation quotation) {
        FlowerOrder preview = FlowerOrder.fromQuotation(quotation, 0L);

        preview.stemsBySpecies().forEach((speciesId, quantity) -> {
            FlowerStock row = stock.findBySpeciesId(speciesId)
                    .orElseThrow(() -> new UnavailableSpeciesException(
                            nameOf(preview, speciesId), "sin informacion de inventario"));

            if (!row.canFulfil(quantity)) {
                throw new UnavailableSpeciesException(
                        nameOf(preview, speciesId), "ya no hay tallos suficientes");
            }
        });
    }

    private static String nameOf(FlowerOrder order, Long speciesId) {
        return order.items().stream()
                .flatMap(item -> item.species().stream())
                .filter(line -> line.speciesId().equals(speciesId))
                .map(OrderItemSpecies::commonNameSnapshot)
                .findFirst()
                .orElse(String.valueOf(speciesId));
    }
}
