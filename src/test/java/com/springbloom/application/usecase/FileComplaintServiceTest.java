package com.springbloom.application.usecase;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.springbloom.domain.model.Complaint;
import com.springbloom.domain.model.ComplaintStatus;
import com.springbloom.domain.model.ComplaintType;
import com.springbloom.domain.model.Conversation;
import com.springbloom.domain.port.in.FileComplaintUseCase.FileComplaintCommand;
import com.springbloom.domain.port.out.ComplaintRepository;
import com.springbloom.domain.port.out.ConversationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Orchestration only: the database is mocked. */
class FileComplaintServiceTest {

    private static final String SESSION = "session-abc";
    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-26T15:00:00Z");

    private ComplaintRepository complaints;
    private ConversationRepository conversations;
    private FileComplaintService service;

    @BeforeEach
    void setUp() {
        complaints = mock(ComplaintRepository.class);
        conversations = mock(ConversationRepository.class);
        service = new FileComplaintService(complaints, conversations);

        when(conversations.findOrStart(SESSION)).thenReturn(
                new Conversation(CONVERSATION_ID, null, SESSION, List.of(), NOW));
        when(complaints.findOrderIdByNumber(anyString())).thenReturn(Optional.empty());

        // The adapter owns numbering, so the double just stamps an id and a number.
        when(complaints.save(any())).thenAnswer(invocation -> {
            Complaint complaint = invocation.getArgument(0);
            return complaint.withId(7L).withNumber("REC-20260826-00042");
        });
    }

    private Complaint captureSaved() {
        ArgumentCaptor<Complaint> captor = ArgumentCaptor.forClass(Complaint.class);
        verify(complaints).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("a claim is filed open, numbered by the adapter, tied to its conversation")
    void filesAnOpenClaim() {
        Complaint filed = service.file(new FileComplaintCommand(
                SESSION, ComplaintType.DAMAGED_FLOWERS, "Llegaron marchitas", null));

        assertThat(filed.complaintNumber()).isEqualTo("REC-20260826-00042");

        Complaint saved = captureSaved();
        assertThat(saved.persisted()).isFalse();
        assertThat(saved.complaintNumber()).as("the adapter numbers it").isNull();
        assertThat(saved.status()).isEqualTo(ComplaintStatus.OPEN);
        assertThat(saved.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(saved.type()).isEqualTo(ComplaintType.DAMAGED_FLOWERS);
    }

    @Test
    @DisplayName("a known order number is resolved to its id")
    void attachesTheOrderWhenItExists() {
        when(complaints.findOrderIdByNumber("ORD-20260801-12345")).thenReturn(Optional.of(3L));

        service.file(new FileComplaintCommand(
                SESSION, ComplaintType.LATE_DELIVERY, "Llego tarde", "ORD-20260801-12345"));

        assertThat(captureSaved().order()).contains(3L);
    }

    @Test
    @DisplayName("an order number we cannot find does not lose the claim")
    void stillFilesWhenTheOrderIsUnknown() {
        when(complaints.findOrderIdByNumber("ORD-NOPE")).thenReturn(Optional.empty());

        Complaint filed = service.file(new FileComplaintCommand(
                SESSION, ComplaintType.NOT_DELIVERED, "Nunca llego", "ORD-NOPE"));

        assertThat(filed.complaintNumber()).isNotNull();
        assertThat(captureSaved().order())
                .as("recorded without an order rather than refused")
                .isEmpty();
    }

    @Test
    @DisplayName("no order number means no lookup at all")
    void skipsTheLookupWhenNoNumberIsGiven() {
        service.file(new FileComplaintCommand(
                SESSION, ComplaintType.OTHER, "Una duda sobre mi pedido", null));

        verify(complaints, never()).findOrderIdByNumber(anyString());
    }

    @Test
    @DisplayName("a blank order number is treated as none, not as a lookup for empty string")
    void treatsABlankOrderNumberAsAbsent() {
        FileComplaintCommand command = new FileComplaintCommand(
                SESSION, ComplaintType.OTHER, "Algo paso", "   ");

        assertThat(command.order()).isEmpty();
        service.file(command);
        verify(complaints, never()).findOrderIdByNumber(anyString());
    }

    @Test
    @DisplayName("the command refuses a claim with nothing in it")
    void guardsItsCommand() {
        assertThatThrownBy(() -> new FileComplaintCommand(SESSION, ComplaintType.OTHER, " ", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FileComplaintCommand(SESSION, null, "algo", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FileComplaintCommand(" ", ComplaintType.OTHER, "algo", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
