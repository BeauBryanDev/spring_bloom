package com.springbloom.adapter.out.persistence.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.springbloom.domain.model.ProductType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Read-only: arrangements are loaded by seed_creations.sql, never written by the app. */
@Entity
@Table(name = "creation")
@Immutable
@Getter
@NoArgsConstructor
public class CreationEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "creation_key", nullable = false, unique = true, length = 60)
    private String creationKey;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "product_type", nullable = false, columnDefinition = "product_type")
    private ProductType productType;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "image_file", nullable = false, length = 150)
    private String imageFile;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "creation")
    @OrderBy("position")
    private List<CreationSpeciesEntity> ingredients = new ArrayList<>();
}
