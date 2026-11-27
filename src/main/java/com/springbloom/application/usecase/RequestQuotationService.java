package com.springbloom.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.springbloom.domain.model.Creation;
import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.Quotation;
import com.springbloom.domain.model.QuotationItem;
import com.springbloom.domain.port.in.RequestQuotationUseCase;
import com.springbloom.domain.port.out.ConversationRepository;
import com.springbloom.domain.port.out.CreationRepository;
import com.springbloom.domain.port.out.FlowerSpeciesRepository;
import com.springbloom.domain.port.out.FlowerStockRepository;
import com.springbloom.domain.port.out.QuotationRepository;
import com.springbloom.domain.service.QuotationComposer;
import com.springbloom.domain.service.QuotationComposer.Selection;
import com.springbloom.domain.service.UnavailableCreationException;
import com.springbloom.domain.service.UnavailableSpeciesException;
import com.springbloom.domain.service.UnknownSpeciesException;

import lombok.RequiredArgsConstructor;

/**
 * Orchestration only: resolve keys to live species and stock, let the composer
 * price them, persist through the port. No pricing and no numbering happen here.
 */
@Service
@RequiredArgsConstructor
public class RequestQuotationService implements RequestQuotationUseCase {

    private static final Logger log = LoggerFactory.getLogger(RequestQuotationService.class);
    private static final Duration VALIDITY = Duration.ofDays(5);

    private final FlowerSpeciesRepository speciesRepository;
    private final FlowerStockRepository stockRepository;
    private final QuotationComposer composer;
    private final QuotationRepository quotationRepository;
    private final ConversationRepository conversationRepository;
    private final CreationRepository creationRepository;
    private final Clock storeClock;

    /**
     * UnavailableSpeciesException is deliberately not caught: an unsellable
     * flower is an answer the agent must give the customer, not a failure.
     */
    @Override
    public Quotation requestQuotation(RequestQuotationCommand command) {
        List<QuotationItem> items = command.lines().stream()
                .map(line -> composer.composeItem(
                        line.productType(), line.discountPercentage(), resolve(line)))
                .toList();

        // The session key is resolved here rather than trusted from the caller: a
        // quotation with a null conversation_id can never be found again by chat.
        UUID conversationId = conversationRepository.findOrStart(command.sessionKey()).id();

        return save(command.sessionKey(), items, conversationId);
    }

    @Override
    public Quotation requestArrangement(String sessionKey, String creationKey, int units) {
        if (sessionKey == null || sessionKey.isBlank()) {
            throw new IllegalArgumentException("A session key is required");
        }
        Creation creation = creationRepository.findByKey(creationKey)
                .filter(Creation::active)
                .orElseThrow(() -> new UnavailableCreationException(creationKey));
        QuotationItem item = QuotationItem.of(creation, units);

        UUID conversationId = conversationRepository.findOrStart(sessionKey).id();
        return save(sessionKey, List.of(item), conversationId);
    }

    private Quotation save(String sessionKey, List<QuotationItem> items, UUID conversationId) {
        Quotation composed = composer.compose(items)
                .withConversation(conversationId)
                .withValidUntil(expiry());

        Quotation saved = quotationRepository.save(composed);

        log.info("Session {} quoted as {} for {}",
                sessionKey, saved.quotationNumber(), saved.totalAmount().amount());

        return saved;
    }

    private List<Selection> resolve(LineRequest line) {
        return line.species().stream()
                .map(requested -> new Selection(
                        species(requested.speciesKey()),
                        stock(requested.speciesKey()),
                        requested.quantity()))
                .toList();
    }

    private FlowerSpecies species(String speciesKey) {
        return speciesRepository.findBySpeciesKey(speciesKey)
                .orElseThrow(() -> new UnknownSpeciesException(speciesKey));
    }

    /**
     * A species with no flower_stock row is unsellable rather than unknown: the
     * schema allows the gap, and the customer needs the same kind of answer as
     * for a NOT_FOR_SALE flower.
     */
    private FlowerStock stock(String speciesKey) {
        
        return stockRepository.findBySpeciesKey(speciesKey)
                .orElseThrow(() -> new UnavailableSpeciesException(
                        speciesKey, "sin informacion de inventario"));
    }

    /** Bogota time, from the same clock the document numbers use. */
    private Instant expiry() {
        return storeClock.instant().plus(VALIDITY).truncatedTo(ChronoUnit.MICROS);
    }
}
