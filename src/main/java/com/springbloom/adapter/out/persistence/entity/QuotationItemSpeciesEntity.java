package com.springbloom.adapter.out.persistence.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The composition of one quotation line, with the snapshots that keep a
 * historical document stable when the catalog or the price list moves.
 */
@Entity
@Table(name = "quotation_item_species")
@Getter
@Setter
@NoArgsConstructor
public class QuotationItemSpeciesEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_item_id", nullable = false)
    private QuotationItemEntity item;

    /**
     * A plain id, not an association: this row points at the species only so the
     * catalog can be traced back, and reading it must never drag live price or
     * stock into a historical document.
     */
    @Column(name = "species_id", nullable = false)
    private Long speciesId;

    @Column(name = "common_name_snapshot", nullable = false, length = 150)
    private String commonNameSnapshot;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /** The effective price at quote time, import multiplier already applied. */
    @Column(name = "unit_price_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @Column(name = "line_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;
}
