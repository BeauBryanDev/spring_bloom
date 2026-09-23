package com.springbloom.adapter.out.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.springbloom.domain.model.QuotationStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The aggregate root: a quotation is saved through this entity or not at all. */
@Entity
@Table(name = "quotation")
@Getter
@Setter
@NoArgsConstructor
public class QuotationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "quotation_number", nullable = false, length = 40, unique = true)
    private String quotationNumber;

    @Column(name = "customer_id")
    private Long customerId;

    /** Null while the conversation is still anonymous. */
    @Column(name = "conversation_id")
    private UUID conversationId;

    /** Postgres enum quotation_status: NAMED_ENUM is what makes the write bind correctly. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "quotation_status")
    private QuotationStatus status;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * One aggregate, one save. orphanRemoval matches the ON DELETE CASCADE in the
     * schema, so dropping a line from this list deletes the row.
     */
    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    private List<QuotationItemEntity> items = new ArrayList<>();

    /** Sets both sides: an item with a null quotation would insert a null FK. */
    public void addItem(QuotationItemEntity item) {
        items.add(item);
        item.setQuotation(this);
    }

    @PrePersist
    void onInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
