package com.springbloom.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.springbloom.domain.model.Complaint;
import com.springbloom.domain.model.ComplaintStatus;
import com.springbloom.domain.model.ComplaintType;
import com.springbloom.domain.port.out.ComplaintRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewComplaintsServiceTest {

    private static final String NUMBER = "REC-20260801-12345";
    private static final Instant NOW = Instant.parse("2026-08-26T15:00:00.123456789Z");

    private ComplaintRepository complaints;
    private ReviewComplaintsService service;

    @BeforeEach
    void setUp() {
        complaints = mock(ComplaintRepository.class);
        service = new ReviewComplaintsService(complaints, Clock.fixed(NOW, ZoneOffset.UTC));
        when(complaints.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private Complaint open() {
        return Complaint.filed(ComplaintType.DAMAGED_FLOWERS, "Llegaron marchitas").withNumber(NUMBER).withId(3L);
    }

    @Test
    @DisplayName("taking a claim into review keeps it open")
    void takesIntoReview() {
        when(complaints.findByNumber(NUMBER)).thenReturn(Optional.of(open()));

        Complaint result = service.takeIntoReview(NUMBER);

        assertThat(result.status()).isEqualTo(ComplaintStatus.IN_REVIEW);
        assertThat(result.open()).isTrue();
        assertThat(result.resolvedAt()).isNull();
    }

    @Test
    @DisplayName("resolving stamps the store clock, truncated to microseconds, and keeps the note")
    void resolves() {
        when(complaints.findByNumber(NUMBER)).thenReturn(Optional.of(open()));

        Complaint result = service.resolve(NUMBER, ComplaintStatus.RESOLVED, "Se repuso el pedido");

        assertThat(result.status()).isEqualTo(ComplaintStatus.RESOLVED);
        assertThat(result.resolution()).isEqualTo("Se repuso el pedido");
        assertThat(result.resolvedAt()).isEqualTo(Instant.parse("2026-08-26T15:00:00.123456Z"));
    }

    @Test
    @DisplayName("a rejection is a close like any other")
    void rejects() {
        when(complaints.findByNumber(NUMBER)).thenReturn(Optional.of(open()));

        assertThat(service.resolve(NUMBER, ComplaintStatus.REJECTED, "Fuera de ventana").status())
                .isEqualTo(ComplaintStatus.REJECTED);
    }

    @Test
    @DisplayName("closing needs a resolution note, and IN_REVIEW or OPEN cannot be an outcome")
    void refusesBadCloses() {
        when(complaints.findByNumber(NUMBER)).thenReturn(Optional.of(open()));

        assertThatThrownBy(() -> service.resolve(NUMBER, ComplaintStatus.RESOLVED, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.resolve(NUMBER, ComplaintStatus.IN_REVIEW, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(complaints, never()).save(any());
    }

    @Test
    @DisplayName("an unknown number is refused on every action")
    void unknownNumber() {
        when(complaints.findByNumber("REC-NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.takeIntoReview("REC-NOPE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.resolve("REC-NOPE", ComplaintStatus.RESOLVED, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(complaints, never()).save(any());
    }

    @Test
    @DisplayName("listing delegates to the repository")
    void lists() {
        when(complaints.findAll()).thenReturn(List.of(open()));

        assertThat(service.listAll()).hasSize(1);
    }
}
