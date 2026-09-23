package com.springbloom.adapter.out.persistence.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.springbloom.domain.model.Complaint;
import com.springbloom.domain.model.ComplaintStatus;
import com.springbloom.domain.model.ComplaintType;
import com.springbloom.domain.port.out.ComplaintRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Requires V2 to have been applied. complaint_type and complaint_status are the
 * fourth and fifth Postgres enums in this schema, and only a real write proves
 * they bind.
 *
 *   set -a; . ./.env; set +a; ./mvnw test
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_USER", matches = ".+")
class ComplaintRepositoryAdapterTest {

    @Autowired
    private ComplaintRepository complaints;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> written = new ArrayList<>();

    @AfterEach
    void removeWhatThisTestWrote() {
        written.forEach(id -> jdbc.update("DELETE FROM complaint WHERE id = ?", id));
        written.clear();
    }

    private Complaint file(ComplaintType type, String description) {
        Complaint saved = complaints.save(Complaint.filed(type, description));
        written.add(saved.id());
        return saved;
    }

    @Test
    @DisplayName("a real save inserts the claim and reads back identical")
    void savesAndReadsBack() {
        Complaint saved = file(ComplaintType.DAMAGED_FLOWERS, "Las rosas llegaron marchitas");

        assertThat(saved.persisted()).isTrue();
        assertThat(saved.complaintNumber()).matches("REC-\\d{8}-\\d{5}");
        assertThat(saved.status()).isEqualTo(ComplaintStatus.OPEN);
        assertThat(saved.createdAt()).isNotNull();

        Complaint read = complaints.findByNumber(saved.complaintNumber()).orElseThrow();
        assertThat(read.id()).isEqualTo(saved.id());
        assertThat(read.description()).isEqualTo("Las rosas llegaron marchitas");
        assertThat(read.type()).isEqualTo(ComplaintType.DAMAGED_FLOWERS);
        assertThat(complaints.findById(saved.id())).isPresent();
    }

    @Test
    @DisplayName("both Postgres enums bind on the write, not just on the read")
    void writesTheEnumColumnsAsNamedEnums() {
        Complaint saved = file(ComplaintType.LATE_DELIVERY, "Llego dos dias tarde");

        assertThat(jdbc.queryForObject(
                "SELECT type::text FROM complaint WHERE id = ?", String.class, saved.id()))
                .isEqualTo("LATE_DELIVERY");
        assertThat(jdbc.queryForObject(
                "SELECT status::text FROM complaint WHERE id = ?", String.class, saved.id()))
                .isEqualTo("OPEN");
    }

    @Test
    @DisplayName("every complaint type survives a round trip")
    void writesEveryType() {
        for (ComplaintType type : ComplaintType.values()) {
            Complaint saved = file(type, "Reclamo de tipo " + type);
            assertThat(complaints.findById(saved.id()).orElseThrow().type()).isEqualTo(type);
        }
    }

    @Test
    @DisplayName("closing a claim writes its resolution and date")
    void closesAClaim() {
        Complaint open = file(ComplaintType.WRONG_ITEM, "Me llegaron claveles, pedi rosas");
        Instant when = Instant.now();

        Complaint closed = complaints.save(
                open.closedAs(ComplaintStatus.RESOLVED, "Se envio el pedido correcto", when));

        assertThat(closed.open()).isFalse();
        assertThat(closed.resolutionText()).contains("Se envio el pedido correcto");
        assertThat(complaints.findById(open.id()).orElseThrow().status())
                .isEqualTo(ComplaintStatus.RESOLVED);
    }

    @Test
    @DisplayName("a claim can be attached to a real seeded order and found by it")
    void findsByOrder() {
        Long orderId = jdbc.queryForObject("SELECT id FROM flower_order LIMIT 1", Long.class);
        String orderNumber = jdbc.queryForObject(
                "SELECT order_number FROM flower_order WHERE id = ?", String.class, orderId);

        assertThat(complaints.findOrderIdByNumber(orderNumber)).contains(orderId);
        assertThat(complaints.findOrderIdByNumber("ORD-does-not-exist")).isEmpty();

        Complaint saved = complaints.save(
                Complaint.filed(ComplaintType.NOT_DELIVERED, "Nunca llego").withOrder(orderId));
        written.add(saved.id());

        assertThat(complaints.findByOrderId(orderId))
                .extracting(Complaint::id)
                .contains(saved.id());
    }

    @Test
    @DisplayName("a claim can be tied to a conversation and found by it")
    void findsByConversation() {
        UUID conversationId = jdbc.queryForObject(
                "SELECT id FROM conversation LIMIT 1", UUID.class);

        Complaint saved = complaints.save(Complaint
                .filed(ComplaintType.OTHER, "Una consulta")
                .withConversation(conversationId));
        written.add(saved.id());

        assertThat(complaints.findByConversationId(conversationId))
                .extracting(Complaint::id)
                .contains(saved.id());
    }
}
