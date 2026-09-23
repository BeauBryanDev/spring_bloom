package com.springbloom.domain.model;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The invariants that mirror the complaint check constraints. Pure domain. */
class ComplaintTest {

    private static final Instant NOW = Instant.parse("2026-08-26T15:00:00Z");

    @Nested
    class Filing {

        @Test
        @DisplayName("a filed claim is open, unnumbered and unresolved")
        void startsOpen() {
            Complaint filed = Complaint.filed(
                    ComplaintType.DAMAGED_FLOWERS, "Llegaron marchitas");

            assertThat(filed.persisted()).isFalse();
            assertThat(filed.complaintNumber()).isNull();
            assertThat(filed.status()).isEqualTo(ComplaintStatus.OPEN);
            assertThat(filed.open()).isTrue();
            assertThat(filed.resolvedAt()).isNull();
            assertThat(filed.order()).isEmpty();
        }

        @Test
        @DisplayName("a claim without a description is refused")
        void needsADescription() {
            assertThatThrownBy(() -> Complaint.filed(ComplaintType.OTHER, "  "))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Complaint.filed(ComplaintType.OTHER, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Complaint.filed(null, "algo"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("an overlong description is refused before it reaches TEXT")
        void capsTheDescription() {
            assertThatThrownBy(() -> Complaint.filed(
                    ComplaintType.OTHER, "x".repeat(Complaint.MAX_DESCRIPTION + 1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Closing {

        private final Complaint open = Complaint.filed(
                ComplaintType.LATE_DELIVERY, "Llego dos dias tarde");

        @Test
        @DisplayName("closing states what was decided and when")
        void closesWithAResolution() {
            Complaint resolved = open.closedAs(
                    ComplaintStatus.RESOLVED, "Se repuso el arreglo", NOW);

            assertThat(resolved.status()).isEqualTo(ComplaintStatus.RESOLVED);
            assertThat(resolved.open()).isFalse();
            assertThat(resolved.resolvedAt()).isEqualTo(NOW);
            assertThat(resolved.resolutionText()).contains("Se repuso el arreglo");
        }

        @Test
        @DisplayName("a closed claim cannot exist without a resolution or a date")
        void refusesAnIncompleteClosure() {
            assertThatThrownBy(() -> open.closedAs(ComplaintStatus.RESOLVED, " ", NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> open.closedAs(ComplaintStatus.REJECTED, "No procede", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("only RESOLVED and REJECTED close a claim")
        void refusesANonClosingStatus() {
            assertThatThrownBy(() -> open.closedAs(ComplaintStatus.OPEN, "algo", NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> open.closedAs(ComplaintStatus.IN_REVIEW, "algo", NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("an open claim may not carry a resolution date")
        void refusesADateWhileOpen() {
            assertThatThrownBy(() -> new Complaint(
                    1L, "REC-20260826-00001", null, null, null,
                    ComplaintType.OTHER, ComplaintStatus.OPEN, "algo", null, NOW, NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("review keeps the claim open")
        void reviewIsNotClosure() {
            Complaint reviewing = open.takenIntoReview();

            assertThat(reviewing.status()).isEqualTo(ComplaintStatus.IN_REVIEW);
            assertThat(reviewing.open()).isTrue();
            assertThat(reviewing.resolvedAt()).isNull();
        }
    }

    @Test
    @DisplayName("every type and status carries the Spanish label the agent reads back")
    void labelsEveryConstant() {
        for (ComplaintType type : ComplaintType.values()) {
            assertThat(type.label()).isNotBlank();
        }
        for (ComplaintStatus status : ComplaintStatus.values()) {
            assertThat(status.label()).isNotBlank();
        }
        assertThat(ComplaintStatus.RESOLVED.closed()).isTrue();
        assertThat(ComplaintStatus.REJECTED.closed()).isTrue();
        assertThat(ComplaintStatus.OPEN.closed()).isFalse();
        assertThat(ComplaintStatus.IN_REVIEW.closed()).isFalse();
    }
}
