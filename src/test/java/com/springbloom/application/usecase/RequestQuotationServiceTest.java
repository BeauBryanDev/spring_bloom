package com.springbloom.application.usecase;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.springbloom.domain.model.Conversation;
import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.FlowerStockStatus;
import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.Quotation;
import com.springbloom.domain.model.QuotationItem;
import com.springbloom.domain.model.QuotationStatus;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.in.RequestQuotationUseCase.LineRequest;
import com.springbloom.domain.port.in.RequestQuotationUseCase.RequestQuotationCommand;
import com.springbloom.domain.port.in.RequestQuotationUseCase.SpeciesRequest;
import com.springbloom.domain.port.out.ConversationRepository;
import com.springbloom.domain.port.out.FlowerSpeciesRepository;
import com.springbloom.domain.port.out.FlowerStockRepository;
import com.springbloom.domain.port.out.QuotationRepository;
import com.springbloom.domain.service.QuotationComposer;
import com.springbloom.domain.service.UnavailableSpeciesException;
import com.springbloom.domain.service.UnknownSpeciesException;
import com.springbloom.domain.service.pricing.BouquetPricingStrategy;
import com.springbloom.domain.service.pricing.GarlandPricingStrategy;
import com.springbloom.domain.service.pricing.IndividualPricingStrategy;
import com.springbloom.domain.service.pricing.PricingStrategyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Orchestration only: pricing is the real composer, the database is mocked. */
class RequestQuotationServiceTest {

    private static final String SESSION = "session-abc";
    private static final Instant NOW = Instant.parse("2026-08-26T15:00:00Z");
    private static final Clock FIXED = Clock.fixed(NOW, ZoneId.of("America/Bogota"));

    private static final UUID CONVERSATION_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private FlowerSpeciesRepository speciesRepository;
    private FlowerStockRepository stockRepository;
    private QuotationRepository quotationRepository;
    private ConversationRepository conversationRepository;
    private RequestQuotationService service;

    @BeforeEach
    void setUp() {
        speciesRepository = mock(FlowerSpeciesRepository.class);
        stockRepository = mock(FlowerStockRepository.class);
        quotationRepository = mock(QuotationRepository.class);
        conversationRepository = mock(ConversationRepository.class);

        when(conversationRepository.findOrStart(SESSION)).thenReturn(new Conversation(
                CONVERSATION_ID, null, SESSION, List.of(), NOW));

        QuotationComposer composer = new QuotationComposer(new PricingStrategyFactory(List.of(
                new IndividualPricingStrategy(),
                new BouquetPricingStrategy(),
                new GarlandPricingStrategy())));

        service = new RequestQuotationService(speciesRepository, stockRepository, composer,
                quotationRepository, conversationRepository, FIXED);

        // The adapter owns numbering, so the double just stamps an id and a number.
        when(quotationRepository.save(any())).thenAnswer(invocation -> {
            Quotation quotation = invocation.getArgument(0);
            return quotation.withId(42L).withNumber("COT-20260826-00001");
        });
    }

    private void catalog(long id, String key, String price, FlowerStockStatus status, int onHand) {
        FlowerSpecies species = new FlowerSpecies(
                id, key, "Nombre " + key, "Scientific " + key, "Colombia", null, null);
        FlowerStock stock = new FlowerStock(
                id, id, status, onHand, null, Money.of(price), new BigDecimal("1.000"), NOW);

        when(speciesRepository.findBySpeciesKey(key)).thenReturn(Optional.of(species));
        when(stockRepository.findBySpeciesKey(key)).thenReturn(Optional.of(stock));
    }

    @Test
    @DisplayName("an omitted bouquet discount is filled in by the shop's volume policy")
    void omittedDiscountComesFromThePolicy() {
        catalog(1L, "rose", "1000.00", FlowerStockStatus.IN_STOCK, 500);
        catalog(2L, "Carnation", "1000.00", FlowerStockStatus.IN_STOCK, 500);

        service.requestQuotation(new RequestQuotationCommand(SESSION, List.of(
                new LineRequest(ProductType.BOUQUET, null, List.of(
                        new SpeciesRequest("rose", 8),
                        new SpeciesRequest("Carnation", 7))))));

        // 15 stems lands on the 15% tier, the same figure every catalog card shows.
        assertThat(captureSaved().items().get(0).discountPercentage())
                .isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    @DisplayName("the policy reads the real stem count, not the number of species")
    void policyUsesTotalStems() {
        catalog(1L, "rose", "1000.00", FlowerStockStatus.IN_STOCK, 500);

        service.requestQuotation(new RequestQuotationCommand(SESSION, List.of(
                new LineRequest(ProductType.BOUQUET, null,
                        List.of(new SpeciesRequest("rose", 60))))));

        assertThat(captureSaved().items().get(0).discountPercentage())
                .isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    @DisplayName("an explicit discount still wins: the REST API may override the policy")
    void explicitDiscountOverridesThePolicy() {
        catalog(1L, "rose", "1000.00", FlowerStockStatus.IN_STOCK, 500);

        service.requestQuotation(new RequestQuotationCommand(SESSION, List.of(
                new LineRequest(ProductType.BOUQUET, new BigDecimal("40.00"),
                        List.of(new SpeciesRequest("rose", 15))))));

        assertThat(captureSaved().items().get(0).discountPercentage())
                .isEqualByComparingTo(new BigDecimal("40.00"));
    }

    private static LineRequest individual(String key, int quantity) {
        return new LineRequest(ProductType.INDIVIDUAL, null,
                List.of(new SpeciesRequest(key, quantity)));
    }

    private Quotation captureSaved() {
        ArgumentCaptor<Quotation> captor = ArgumentCaptor.forClass(Quotation.class);
        verify(quotationRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("keys are resolved to live species and stock, and the totals come from the composer")
    void pricesTheRequestedLines() {
        catalog(1L, "rose", "4448.00", FlowerStockStatus.IN_STOCK, 500);
        catalog(2L, "Carnation", "1000.00", FlowerStockStatus.IN_STOCK, 500);

        Quotation saved = service.requestQuotation(new RequestQuotationCommand(SESSION, List.of(
                individual("rose", 3),
                new LineRequest(ProductType.BOUQUET, new BigDecimal("10.00"),
                        List.of(new SpeciesRequest("Carnation", 6),
                                new SpeciesRequest("rose", 2))))));

        assertThat(saved.id()).isEqualTo(42L);
        assertThat(saved.quotationNumber()).isEqualTo("COT-20260826-00001");

        Quotation composed = captureSaved();
        assertThat(composed.items()).hasSize(2);
        assertThat(composed.totalStems()).isEqualTo(11);

        // rose 3x4448 = 13344; bouquet (6x1000 + 2x4448) = 14896, less 10% = 13406.40
        assertThat(composed.subtotal()).isEqualTo(Money.of("28240.00"));
        assertThat(composed.totalAmount()).isEqualTo(Money.of("26750.40"));
        assertThat(composed.discountAmount()).isEqualTo(Money.of("1489.60"));
    }

    @Test
    @DisplayName("valid_until is five days out on the store clock, per manual section 14")
    void stampsFiveDaysOfValidity() {
        catalog(1L, "rose", "4448.00", FlowerStockStatus.IN_STOCK, 500);

        service.requestQuotation(new RequestQuotationCommand(SESSION, List.of(individual("rose", 1))));

        assertThat(captureSaved().validUntil()).isEqualTo(Instant.parse("2026-08-31T15:00:00Z"));
    }

    @Test
    @DisplayName("what reaches the repository is an unsaved DRAFT: the adapter numbers it")
    void handsTheAdapterAnUnnumberedDraft() {
        catalog(1L, "rose", "4448.00", FlowerStockStatus.IN_STOCK, 500);

        service.requestQuotation(new RequestQuotationCommand(SESSION, List.of(individual("rose", 1))));

        Quotation composed = captureSaved();
        assertThat(composed.persisted()).isFalse();
        assertThat(composed.quotationNumber()).isNull();
        assertThat(composed.status()).isEqualTo(QuotationStatus.DRAFT);
    }

    @Test
    @DisplayName("the session key is resolved to a conversation and stamped on the quotation")
    void tiesTheQuotationToItsConversation() {
        catalog(1L, "rose", "4448.00", FlowerStockStatus.IN_STOCK, 500);

        service.requestQuotation(new RequestQuotationCommand(SESSION, List.of(individual("rose", 1))));

        verify(conversationRepository).findOrStart(SESSION);
        assertThat(captureSaved().conversationId())
                .as("otherwise findByConversationId could never find it again")
                .isEqualTo(CONVERSATION_ID);
    }

    @Test
    @DisplayName("a species that is not in the catalog is unknown, not unavailable")
    void rejectsAnUnknownSpeciesKey() {
        when(speciesRepository.findBySpeciesKey("not_a_flower")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestQuotation(
                new RequestQuotationCommand(SESSION, List.of(individual("not_a_flower", 1)))))
                .isInstanceOf(UnknownSpeciesException.class)
                .extracting("speciesKey").isEqualTo("not_a_flower");

        verify(quotationRepository, never()).save(any());
    }

    @Test
    @DisplayName("a species with no stock row is unavailable rather than unknown")
    void treatsAMissingStockRowAsUnavailable() {
        FlowerSpecies orphan = new FlowerSpecies(
                9L, "monkshood", "Aconito", "Aconitum", "Colombia", null, null);
        when(speciesRepository.findBySpeciesKey("monkshood")).thenReturn(Optional.of(orphan));
        when(stockRepository.findBySpeciesKey("monkshood")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestQuotation(
                new RequestQuotationCommand(SESSION, List.of(individual("monkshood", 1)))))
                .isInstanceOf(UnavailableSpeciesException.class)
                .extracting("speciesKey").isEqualTo("monkshood");
    }

    @Test
    @DisplayName("an unsellable flower surfaces the domain exception rather than a quotation")
    void doesNotSwallowUnavailableSpecies() {
        catalog(5L, "monkshood", "1000.00", FlowerStockStatus.NOT_FOR_SALE, 0);

        assertThatThrownBy(() -> service.requestQuotation(
                new RequestQuotationCommand(SESSION, List.of(individual("monkshood", 1)))))
                .isInstanceOf(UnavailableSpeciesException.class)
                .extracting("reason").isEqualTo(FlowerStockStatus.NOT_FOR_SALE.label());

        verify(quotationRepository, never()).save(any());
    }

    @Test
    @DisplayName("ordering more than is on hand is refused before anything is persisted")
    void refusesMoreStemsThanAreOnHand() {
        catalog(1L, "rose", "4448.00", FlowerStockStatus.IN_STOCK, 4);

        assertThatThrownBy(() -> service.requestQuotation(
                new RequestQuotationCommand(SESSION, List.of(individual("rose", 10)))))
                .isInstanceOf(UnavailableSpeciesException.class)
                .hasMessageContaining("solo quedan 4");

        verify(quotationRepository, never()).save(any());
    }

    @Test
    @DisplayName("the discount rule the schema enforces is enforced before the insert")
    void rejectsADiscountOnAnIndividualLine() {
        catalog(1L, "rose", "4448.00", FlowerStockStatus.IN_STOCK, 500);

        LineRequest illegal = new LineRequest(ProductType.INDIVIDUAL, new BigDecimal("10.00"),
                List.of(new SpeciesRequest("rose", 1)));

        assertThatThrownBy(() -> service.requestQuotation(
                new RequestQuotationCommand(SESSION, List.of(illegal))))
                .isInstanceOf(IllegalArgumentException.class);

        verify(quotationRepository, never()).save(any());
    }

    @Test
    @DisplayName("an empty request is refused by the command, not by the database")
    void refusesAnEmptyRequest() {
        assertThatThrownBy(() -> new RequestQuotationCommand(SESSION, List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RequestQuotationCommand(" ", List.of(individual("rose", 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a garland is priced by its own strategy, not the bouquet's")
    void composesAGarland() {
        catalog(1L, "rose", "1000.00", FlowerStockStatus.IN_STOCK, 500);

        service.requestQuotation(new RequestQuotationCommand(SESSION, List.of(
                new LineRequest(ProductType.GARLAND, new BigDecimal("20.00"),
                        List.of(new SpeciesRequest("rose", 10))))));

        QuotationItem line = captureSaved().items().get(0);
        assertThat(line.productType()).isEqualTo(ProductType.GARLAND);
        assertThat(line.composedSubtotal()).isEqualTo(Money.of("10000.00"));
        assertThat(line.discountAmount()).isEqualTo(Money.of("2000.00"));
    }
}
