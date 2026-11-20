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

import com.springbloom.domain.model.Conversation;
import com.springbloom.domain.model.Customer;
import com.springbloom.domain.model.FlowerOrder;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.FlowerStockStatus;
import com.springbloom.domain.model.OrderStatus;
import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.Quotation;
import com.springbloom.domain.model.QuotationItem;
import com.springbloom.domain.model.QuotationItemSpecies;
import com.springbloom.domain.model.QuotationStatus;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.springbloom.domain.port.out.ConversationRepository;
import com.springbloom.domain.port.out.CustomerRepository;
import com.springbloom.domain.port.out.FlowerOrderRepository;
import com.springbloom.domain.port.out.FlowerStockRepository;
import com.springbloom.domain.port.out.QuotationRepository;
import com.springbloom.domain.service.UnavailableSpeciesException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceOrderServiceTest {

    private static final String SESSION = "session-abc";
    private static final UUID CONVERSATION = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant NOW = Instant.parse("2026-08-26T15:00:00Z");
    private static final String NUMBER = "COT-20260826-12345";

    private QuotationRepository quotations;
    private FlowerOrderRepository orders;
    private CustomerRepository customers;
    private ConversationRepository conversations;
    private FlowerStockRepository stock;
    private PlaceOrderService service;

    @BeforeEach
    void setUp() {
        quotations = mock(QuotationRepository.class);
        orders = mock(FlowerOrderRepository.class);
        customers = mock(CustomerRepository.class);
        conversations = mock(ConversationRepository.class);
        stock = mock(FlowerStockRepository.class);
        service = new PlaceOrderService(quotations, orders, customers, conversations, stock,
                Clock.fixed(NOW, ZoneId.of("America/Bogota")));

        when(conversations.findBySessionKey(SESSION)).thenReturn(Optional.of(
                new Conversation(CONVERSATION, null, SESSION, List.of(), NOW)));
        when(orders.findByQuotationId(7L)).thenReturn(Optional.empty());
        when(customers.save(any())).thenAnswer(call -> ((Customer) call.getArgument(0)).withId(9L));
        when(orders.save(any())).thenAnswer(call ->
                ((FlowerOrder) call.getArgument(0)).withId(1L).withNumber("ORD-20260826-54321"));
        when(stock.findBySpeciesId(1L)).thenReturn(Optional.of(stock(FlowerStockStatus.IN_STOCK, 50)));
    }

    private static FlowerStock stock(FlowerStockStatus status, int quantity) {
        return new FlowerStock(1L, 1L, status, quantity, null, Money.of("1000"), BigDecimal.ONE, NOW);
    }

    private static Quotation quotation(UUID conversation, Instant validUntil, QuotationStatus status) {
        QuotationItem item = new QuotationItem(ProductType.INDIVIDUAL, null,
                List.of(new QuotationItemSpecies(1L, "Rosa", 10, Money.of("1000"), Money.of("10000"))),
                Money.of("10000"), Money.of("10000"));
        return new Quotation(7L, NUMBER, null, conversation, status, List.of(item),
                Money.of("10000"), Money.ZERO, Money.of("10000"), validUntil);
    }

    private static PlaceOrderCommand command() {
        return new PlaceOrderCommand(SESSION, NUMBER, "Ana Perez", "3001234567", "Calle 1 # 2-3", null);
    }

    @Test
    @DisplayName("a quotation of this session becomes a PENDING order and is marked ACCEPTED")
    void placesOrder() {
        when(quotations.findByNumber(NUMBER)).thenReturn(Optional.of(
                quotation(CONVERSATION, NOW.plusSeconds(3600), QuotationStatus.DRAFT)));

        FlowerOrder order = service.place(command());

        assertThat(order.orderNumber()).isEqualTo("ORD-20260826-54321");
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.customerId()).isEqualTo(9L);
        verify(quotations).updateStatus(7L, QuotationStatus.ACCEPTED);
        verify(stock, never()).decrementIfAvailable(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("another session's quotation looks exactly like an unknown one")
    void foreignQuotationIsNotFound() {
        when(quotations.findByNumber(NUMBER)).thenReturn(Optional.of(
                quotation(UUID.randomUUID(), NOW.plusSeconds(3600), QuotationStatus.DRAFT)));

        assertThatThrownBy(() -> service.place(command()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No encontramos esa cotizacion");
        verify(orders, never()).save(any());
    }

    @Test
    @DisplayName("an expired quotation cannot become an order")
    void expiredQuotation() {
        when(quotations.findByNumber(NUMBER)).thenReturn(Optional.of(
                quotation(CONVERSATION, NOW.minusSeconds(1), QuotationStatus.DRAFT)));

        assertThatThrownBy(() -> service.place(command()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vencio");
        verify(customers, never()).save(any());
    }

    @Test
    @DisplayName("placing the same quotation twice returns the order that exists")
    void idempotent() {
        FlowerOrder existing = FlowerOrder.fromQuotation(
                quotation(CONVERSATION, NOW.plusSeconds(3600), QuotationStatus.ACCEPTED), 9L)
                .withId(1L).withNumber("ORD-20260826-11111");
        when(quotations.findByNumber(NUMBER)).thenReturn(Optional.of(
                quotation(CONVERSATION, NOW.plusSeconds(3600), QuotationStatus.ACCEPTED)));
        when(orders.findByQuotationId(7L)).thenReturn(Optional.of(existing));

        assertThat(service.place(command())).isSameAs(existing);
        verify(orders, never()).save(any());
        verify(customers, never()).save(any());
    }

    @Test
    @DisplayName("stock that no longer covers the quotation is refused before anything is written")
    void insufficientStock() {
        when(quotations.findByNumber(NUMBER)).thenReturn(Optional.of(
                quotation(CONVERSATION, NOW.plusSeconds(3600), QuotationStatus.DRAFT)));
        when(stock.findBySpeciesId(1L)).thenReturn(Optional.of(stock(FlowerStockStatus.IN_STOCK, 3)));

        assertThatThrownBy(() -> service.place(command()))
                .isInstanceOf(UnavailableSpeciesException.class);
        verify(customers, never()).save(any());
        verify(orders, never()).save(any());
    }

    @Test
    @DisplayName("missing delivery data fails before an order exists")
    void incompleteCustomer() {
        when(quotations.findByNumber(NUMBER)).thenReturn(Optional.of(
                quotation(CONVERSATION, NOW.plusSeconds(3600), QuotationStatus.DRAFT)));

        PlaceOrderCommand noAddress = new PlaceOrderCommand(SESSION, NUMBER, "Ana", "3001234567", " ", null);

        assertThatThrownBy(() -> service.place(noAddress)).isInstanceOf(IllegalArgumentException.class);
        verify(orders, never()).save(any());
    }
}
