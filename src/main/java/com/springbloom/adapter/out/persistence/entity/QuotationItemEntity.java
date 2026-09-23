package com.springbloom.adapter.out.persistence.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.springbloom.domain.model.ProductType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One line of a quotation. This side owns the foreign key, which is why the
 * root maps the collection with mappedBy = "quotation".
 */
@Entity
@Table(name = "quotation_item")
@Getter
@Setter
@NoArgsConstructor
public class QuotationItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_id", nullable = false)
    private QuotationEntity quotation;

    /** Postgres enum product_type, same binding rule as quotation.status. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "product_type", nullable = false, columnDefinition = "product_type")
    private ProductType productType;

    /**
     * Null for INDIVIDUAL, set for BOUQUET and GARLAND. The database enforces
     * this with ck_quotation_item_discount_only_bundle; QuotationItem enforces
     * it in memory, so an invalid line never reaches an INSERT.
     */
    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    /** What the customer pays for this line, after the pricing strategy. */
    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    /**
     * Loaded by a batched second query rather than by the entity graph: joining
     * this list and quotation.items in one select is two bags, which Hibernate
     * rejects with MultipleBagFetchException. One extra query beats a List to Set
     * rewrite and the cartesian product a double join would produce.
     */
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 32)
    private List<QuotationItemSpeciesEntity> species = new ArrayList<>();

    public void addSpecies(QuotationItemSpeciesEntity line) {
        species.add(line);
        line.setItem(this);
    }
}
