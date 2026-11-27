package com.springbloom.adapter.out.persistence.mapper;

import java.math.BigDecimal;
import java.util.List;

import com.springbloom.adapter.out.persistence.entity.QuotationEntity;
import com.springbloom.adapter.out.persistence.entity.QuotationItemEntity;
import com.springbloom.adapter.out.persistence.entity.QuotationItemSpeciesEntity;
import com.springbloom.domain.model.CreationLine;
import com.springbloom.domain.model.Quotation;
import com.springbloom.domain.model.QuotationItem;
import com.springbloom.domain.model.QuotationItemSpecies;
import com.springbloom.domain.model.vo.Money;

/**
 * Translates the quotation aggregate in both directions. toDomain walks lazy
 * collections, so it must be called inside the adapter's transaction.
 */
public class QuotationMapper {

    private QuotationMapper() {
    }

    public static QuotationEntity toEntity(Quotation quotation) {
        QuotationEntity entity = new QuotationEntity();
        entity.setId(quotation.id());
        entity.setQuotationNumber(quotation.quotationNumber());
        entity.setCustomerId(quotation.customerId());
        entity.setConversationId(quotation.conversationId());
        entity.setStatus(quotation.status());
        entity.setSubtotal(quotation.subtotal().amount());
        entity.setDiscountAmount(quotation.discountAmount().amount());
        entity.setTotalAmount(quotation.totalAmount().amount());
        entity.setValidUntil(quotation.validUntil());

        for (QuotationItem item : quotation.items()) {
            entity.addItem(toEntity(item));
        }
        return entity;
    }

    private static QuotationItemEntity toEntity(QuotationItem item) {
        QuotationItemEntity entity = new QuotationItemEntity();
        entity.setProductType(item.productType());
        entity.setDiscountPercentage(item.discountPercentage());
        entity.setSubtotal(item.subtotal().amount());
        if (item.creation() != null) {
            CreationLine creation = item.creation();
            entity.setCreationId(creation.creationId());
            entity.setCreationNameSnapshot(creation.nameSnapshot());
            entity.setUnits(creation.units());
            entity.setUnitPriceSnapshot(creation.unitPriceSnapshot().amount());
        }

        for (QuotationItemSpecies line : item.species()) {
            entity.addSpecies(toEntity(line));
        }
        return entity;
    }

    private static QuotationItemSpeciesEntity toEntity(QuotationItemSpecies line) {
        QuotationItemSpeciesEntity entity = new QuotationItemSpeciesEntity();
        entity.setSpeciesId(line.speciesId());
        entity.setCommonNameSnapshot(line.commonNameSnapshot());
        entity.setQuantity(line.quantity());
        entity.setUnitPriceSnapshot(line.unitPriceSnapshot().amount());
        entity.setLineTotal(line.lineTotal().amount());
        return entity;
    }

    public static Quotation toDomain(QuotationEntity entity) {
        List<QuotationItem> items = entity.getItems().stream()
                .map(QuotationMapper::toDomain)
                .toList();

        return new Quotation(
                entity.getId(),
                entity.getQuotationNumber(),
                entity.getCustomerId(),
                entity.getConversationId(),
                entity.getStatus(),
                items,
                Money.of(entity.getSubtotal()),
                Money.of(entity.getDiscountAmount()),
                Money.of(entity.getTotalAmount()),
                entity.getValidUntil());
    }

    private static QuotationItem toDomain(QuotationItemEntity entity) {
        List<QuotationItemSpecies> species = entity.getSpecies().stream()
                .map(QuotationMapper::toDomain)
                .toList();

        CreationLine creation = creationOf(entity.getCreationId(), entity.getCreationNameSnapshot(),
                entity.getUnits(), entity.getUnitPriceSnapshot());

        Money composedSubtotal = creation != null
                ? Money.of(entity.getSubtotal())
                : species.stream()
                        .map(QuotationItemSpecies::lineTotal)
                        .reduce(Money.ZERO, Money::plus);

        return new QuotationItem(
                entity.getProductType(),
                entity.getDiscountPercentage(),
                species,
                composedSubtotal,
                Money.of(entity.getSubtotal()),
                creation);
    }

    /** Null on a flower line: the four columns are set together or not at all. */
    static CreationLine creationOf(Long id, String name, Integer units, BigDecimal unitPrice) {
        if (id == null) {
            return null;
        }
        return new CreationLine(id, name, units, Money.of(unitPrice));
    }

    private static QuotationItemSpecies toDomain(QuotationItemSpeciesEntity entity) {
        return new QuotationItemSpecies(
                entity.getSpeciesId(),
                entity.getCommonNameSnapshot(),
                entity.getQuantity(),
                Money.of(entity.getUnitPriceSnapshot()),
                Money.of(entity.getLineTotal()));
    }
}
