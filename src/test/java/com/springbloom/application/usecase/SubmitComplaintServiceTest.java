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
import org.mockito.ArgumentCaptor;

import com.springbloom.domain.model.Complaint;
import com.springbloom.domain.model.ComplaintType;
import com.springbloom.domain.model.Conversation;
import com.springbloom.domain.model.FlowerOrder;
import com.springbloom.domain.model.OrderItem;
import com.springbloom.domain.model.OrderItemSpecies;
import com.springbloom.domain.model.OrderStatus;
import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.Shipment;
import com.springbloom.domain.model.ShipmentStatus;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.in.SubmitComplaintUseCase.Refusal;
import com.springbloom.domain.port.in.SubmitComplaintUseCase.SubmitComplaintCommand;
import com.springbloom.domain.port.in.SubmitComplaintUseCase.SubmitResult;
import com.springbloom.domain.port.out.ComplaintRepository;
import com.springbloom.domain.port.out.ConversationRepository;
import com.springbloom.domain.port.out.FlowerOrderRepository;
import com.springbloom.domain.port.out.ShipmentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubmitComplaintServiceTest {

    private static final String SESSION = "session-abc";
    private static final String ORDER = "ORD-20260801-12345";
    private static final Instant NOW = Instant.parse("2026-08-26T15:00:00Z");
    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    private ConversationRepository conversations;
    private FlowerOrderRepository orders;
    private ShipmentRepository shipments;
    private ComplaintRepository complaints;
    private SubmitComplaintService service;

    @BeforeEach
    void setUp() {
        conversations = mock(ConversationRepository.class);
        orders = mock(FlowerOrderRepository.class);
        shipments = mock(ShipmentRepository.class);
        complaints = mock(ComplaintRepository.class);
        service = new SubmitComplaintService(conversations, orders, shipments, complaints,
                Clock.fixed(NOW, ZoneOffset.UTC));

        verifiedCustomer(7L);
        OrderItemSpecies line = new OrderItemSpecies(
                1L, "Rosa", 3, Money.of("4000"), Money.of("12000"));
        OrderItem item = new OrderItem(
                ProductType.INDIVIDUAL, null, List.of(line), Money.of("12000"));
        when(orders.findByNumber(ORDER)).thenReturn(Optional.of(new FlowerOrder(
                10L, ORDER, 5L, 7L, OrderStatus.DELIVERED, List.of(item),
                Money.of("12000"), Money.of("0"), Money.of("12000"), NOW)));
        when(shipments.findByOrderId(10L)).thenReturn(Optional.empty());
        when(complaints.findByOrderId(10L)).thenReturn(List.of());
        when(complaints.save(any())).thenAnswer(call ->
                ((Complaint) call.getArgument(0)).withId(1L).withNumber("REC-20260826-00001"));
    }

    private void verifiedCustomer(Long customerId) {
        when(conversations.findOrStart(SESSION)).thenReturn(
                new Conversation(CONVERSATION_ID, customerId, SESSION, List.of(), NOW));
    }

    private void deliveredHoursAgo(long hours) {
        Instant delivered = NOW.minusSeconds(hours * 3600);
        when(shipments.findByOrderId(10L)).thenReturn(Optional.of(new Shipment(
                1L, 10L, ShipmentStatus.DELIVERED, "Ana Perez", "Calle 1 # 2-3",
                null, null, delivered.minusSeconds(3600), delivered)));
    }

    private SubmitResult submit(ComplaintType type) {
        return service.submit(new SubmitComplaintCommand(SESSION, ORDER, type, "Llegaron marchitas"));
    }

    @Test
    @DisplayName("nothing is filed for a visitor who has not verified, or for someone else's order")
    void refusesAnUnverifiedVisitor() {
        verifiedCustomer(null);
        assertThat(submit(ComplaintType.DAMAGED_FLOWERS).refusal()).isEqualTo(Refusal.NOT_VERIFIED);

        verifiedCustomer(99L);
        assertThat(submit(ComplaintType.DAMAGED_FLOWERS).refusal()).isEqualTo(Refusal.NOT_VERIFIED);

        verify(complaints, never()).save(any());
    }

    @Test
    @DisplayName("before delivery only a missing or late order can be claimed")
    void undeliveredOrdersAllowOnlyDeliveryClaims() {
        assertThat(submit(ComplaintType.DAMAGED_FLOWERS).refusal()).isEqualTo(Refusal.NOT_DELIVERED_YET);
        assertThat(submit(ComplaintType.NOT_DELIVERED).complaint()).isNotNull();
    }

    @Test
    @DisplayName("a damage claim after 24 hours is still recorded, with a note for the reviewer")
    void lateClaimIsRecordedAndMarked() {
        deliveredHoursAgo(30);

        SubmitResult result = submit(ComplaintType.DAMAGED_FLOWERS);

        ArgumentCaptor<Complaint> saved = ArgumentCaptor.forClass(Complaint.class);
        verify(complaints).save(saved.capture());
        assertThat(result.complaint()).isNotNull();
        assertThat(saved.getValue().description()).contains("Fuera de la ventana de 24 h");
        assertThat(saved.getValue().orderId()).isEqualTo(10L);
        assertThat(saved.getValue().customerId()).isEqualTo(7L);
        assertThat(saved.getValue().conversationId()).isEqualTo(CONVERSATION_ID);
    }

    @Test
    @DisplayName("a second claim of the same type on the same order returns the open one")
    void doesNotDuplicateAnOpenClaim() {
        deliveredHoursAgo(2);
        Complaint open = Complaint.filed(ComplaintType.DAMAGED_FLOWERS, "Ya reportado")
                .withId(3L).withNumber("REC-20260826-00003");
        when(complaints.findByOrderId(10L)).thenReturn(List.of(open));

        SubmitResult result = submit(ComplaintType.DAMAGED_FLOWERS);

        assertThat(result.alreadyOpen()).isTrue();
        assertThat(result.complaint().complaintNumber()).isEqualTo("REC-20260826-00003");
        verify(complaints, never()).save(any());
    }
}
