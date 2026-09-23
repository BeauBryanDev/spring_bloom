package com.springbloom.adapter.out.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.springbloom.domain.model.FlowerStockStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "flower_stock")
@Getter
@Setter
@NoArgsConstructor
public class FlowerStockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "flower_stock_id")
    private Long id;

    @Column(name = "species_id", nullable = false, unique = true)
    private Long speciesId;

    /**
     * status is the Postgres enum flower_stock_status, not a varchar. Mapping it
     * with @Enumerated(STRING) alone reads fine but fails every write with
     * "column status is of type flower_stock_status but expression is of type
     * character varying" - Postgres will not implicitly cast into an enum type.
     * NAMED_ENUM binds the parameter as the enum itself.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "flower_stock_status")
    private FlowerStockStatus status;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "eta_days")
    private Integer etaDays;

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "import_price_multiplier", nullable = false, precision = 6, scale = 3)
    private BigDecimal importPriceMultiplier;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
