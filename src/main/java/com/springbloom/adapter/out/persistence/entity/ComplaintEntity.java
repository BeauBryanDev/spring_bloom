package com.springbloom.adapter.out.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.springbloom.domain.model.ComplaintStatus;
import com.springbloom.domain.model.ComplaintType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A claim row. order_id, customer_id and conversation_id are plain Longs and a
 * UUID rather than associations: a claim points at its order for tracing, and
 * must not become a path from an old claim to today's order state.
 */
@Entity
@Table(name = "complaint")
@Getter
@Setter
@NoArgsConstructor
public class ComplaintEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "complaint_number", nullable = false, length = 40, unique = true)
    private String complaintNumber;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    /** Postgres enum complaint_type: NAMED_ENUM is what makes the write bind. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type", nullable = false, columnDefinition = "complaint_type")
    private ComplaintType type;

    /** Postgres enum complaint_status, same trio. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "complaint_status")
    private ComplaintStatus status;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "resolution", columnDefinition = "TEXT")
    private String resolution;

    /**
     * updatable = false: when a claim is closed the domain object is mapped back
     * to a detached entity and merged, and an UPDATE that carried this column
     * would write whatever the mapper happened to set. A creation date is not a
     * thing an update may change.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @PrePersist
    void onInsert() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
