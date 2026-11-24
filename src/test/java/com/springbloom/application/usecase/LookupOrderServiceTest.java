package com.springbloom.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.springbloom.domain.model.Conversation;
import com.springbloom.domain.model.Customer;
import com.springbloom.domain.model.FlowerOrder;
import com.springbloom.domain.model.OrderItem;
import com.springbloom.domain.model.OrderItemSpecies;
import com.springbloom.domain.model.OrderStatus;
import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.Shipment;
import com.springbloom.domain.model.ShipmentStatus;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.in.LookupOrderUseCase.LookupOrderCommand;
import com.springbloom.domain.port.in.LookupOrderUseCase.Outcome;
import com.springbloom.domain.port.in.LookupOrderUseCase.OrderLookup;
import com.springbloom.domain.port.out.ConversationRepository;
import com.springbloom.domain.port.out.CustomerRepository;
import com.springbloom.domain.port.out.FlowerOrderRepository;
import com.springbloom.domain.port.out.ShipmentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LookupOrderServiceTest {

    private static final String SESSION = "session-abc";
    private static final String ORDER = "ORD-20260801-12345";
    private static final String PHONE = "3001234567";
    private static final Instant NOW = Instant.parse("2026-08-26T15:00:00Z");
    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    private FlowerOrderRepository orders;
    private CustomerRepository customers;
    private ShipmentRepository shipments;
    private ConversationRepository conversations;
    private LookupOrderService service;

    @BeforeEach
    void setUp() {
        orders = mock(FlowerOrderRepository.class);
        customers = mock(CustomerRepository.class);
        shipments = mock(ShipmentRepository.class);
        conversations = mock(ConversationRepository.class);
        service = new LookupOrderService(orders, customers, shipments, conversations,
                Clock.fixed(NOW, ZoneOffset.UTC));

        OrderItemSpecies line = new OrderItemSpecies(
                1L, "Rosa", 3, Money.of("4000"), Money.of("12000"));
        OrderItem item = new OrderItem(
                ProductType.INDIVIDUAL, null, List.of(line), Money.of("12000"));
        when(orders.findByNumber(ORDER)).thenReturn(Optional.of(new FlowerOrder(
                10L, ORDER, 5L, 7L, OrderStatus.DISPATCHED, List.of(item),
                Money.of("12000"), Money.of("0"), Money.of("12000"), NOW)));
        when(customers.findById(7L)).thenReturn(Optional.of(
                new Customer(7L, "Ana Perez", null, PHONE, "Calle 1 # 2-3")));
        when(shipments.findByOrderId(10L)).thenReturn(Optional.empty());
        when(conversations.findOrStart(SESSION)).thenReturn(
                new Conversation(CONVERSATION_ID, null, SESSION, List.of(), NOW));
    }

    private OrderLookup lookup(String order, String phone) {
        return service.lookup(new LookupOrderCommand(SESSION, order, phone));
    }

    private void deliveredHoursAgo(long hours) {
        Instant delivered = NOW.minusSeconds(hours * 3600);
        when(shipments.findByOrderId(10L)).thenReturn(Optional.of(new Shipment(
                1L, 10L, ShipmentStatus.DELIVERED, "Ana Perez", "Calle 1 # 2-3",
                null, null, delivered.minusSeconds(3600), delivered)));
    }

    @Test
    @DisplayName("a formatted phone verifies and ties the conversation to the customer")
    void verifiesAndAttachesTheCustomer() {
        OrderLookup result = lookup(ORDER, "300 123-4567");

        assertThat(result.isVerified()).isTrue();
        assertThat(result.order().items()).containsExactly("3 x Rosa");
        verify(conversations).assignCustomer(CONVERSATION_ID, 7L);
    }

    @Test
    @DisplayName("the report window is 24 hours from delivery: 23h inside, 25h outside")
    void reportsTheWindow() {
        deliveredHoursAgo(23);
        assertThat(lookup(ORDER, PHONE).order().withinReportWindow()).isTrue();

        deliveredHoursAgo(25);
        assertThat(lookup(ORDER, PHONE).order().withinReportWindow()).isFalse();
    }

    @Test
    @DisplayName("an unknown order and a wrong phone are indistinguishable")
    void doesNotRevealWhichPartFailed() {
        when(orders.findByNumber("ORD-NOPE")).thenReturn(Optional.empty());

        assertThat(lookup("ORD-NOPE", PHONE).outcome()).isEqualTo(Outcome.NOT_MATCHED);
        assertThat(lookup(ORDER, "3009999999").outcome()).isEqualTo(Outcome.NOT_MATCHED);
    }

    @Test
    @DisplayName("a malformed phone is a typo, not a guess, and never locks the session")
    void typosDoNotCount() {
        for (int i = 0; i < 5; i++) {
            assertThat(lookup(ORDER, "+57 300 123 4567").outcome()).isEqualTo(Outcome.INVALID_PHONE);
        }
        assertThat(lookup(ORDER, PHONE).isVerified()).isTrue();
    }

    @Test
    @DisplayName("three wrong guesses lock the session, even for the right phone")
    void locksAfterThreeFailures() {
        for (int i = 0; i < 3; i++) {
            lookup(ORDER, "3009999999");
        }
        assertThat(lookup(ORDER, PHONE).outcome()).isEqualTo(Outcome.LOCKED);
    }

    @Test
    @DisplayName("rotating session keys from one IP does not reset the lock")
    void locksByIpAcrossSessions() {
        for (int i = 0; i < 3; i++) {
            service.lookup(new LookupOrderCommand("rotating-" + i, ORDER, "3009999999", "203.0.113.7"));
        }
        when(conversations.findOrStart("fresh-session")).thenReturn(
                new Conversation(CONVERSATION_ID, null, "fresh-session", List.of(), NOW));

        OrderLookup fromSameIp = service.lookup(
                new LookupOrderCommand("fresh-session", ORDER, PHONE, "203.0.113.7"));
        OrderLookup fromOtherIp = service.lookup(
                new LookupOrderCommand("fresh-session", ORDER, PHONE, "198.51.100.9"));

        assertThat(fromSameIp.outcome()).isEqualTo(Outcome.LOCKED);
        assertThat(fromOtherIp.isVerified()).isTrue();
    }

    @Test
    @DisplayName("a success does not erase the IP's earlier failures")
    void successKeepsIpFailures() {
        for (int i = 0; i < 2; i++) {
            service.lookup(new LookupOrderCommand("a-" + i, ORDER, "3009999999", "203.0.113.7"));
        }
        when(conversations.findOrStart("good-one")).thenReturn(
                new Conversation(CONVERSATION_ID, null, "good-one", List.of(), NOW));
        assertThat(service.lookup(new LookupOrderCommand("good-one", ORDER, PHONE, "203.0.113.7"))
                .isVerified()).isTrue();

        service.lookup(new LookupOrderCommand("b", ORDER, "3009999999", "203.0.113.7"));

        assertThat(service.lookup(new LookupOrderCommand("c", ORDER, PHONE, "203.0.113.7")).outcome())
                .isEqualTo(Outcome.LOCKED);
    }
}
