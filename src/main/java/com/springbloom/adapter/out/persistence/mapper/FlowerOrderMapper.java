package com.springbloom.adapter.out.persistence.mapper;

import java.util.List;

import com.springbloom.adapter.out.persistence.entity.FlowerOrderEntity;
import com.springbloom.adapter.out.persistence.entity.OrderItemEntity;
import com.springbloom.adapter.out.persistence.entity.OrderItemSpeciesEntity;
import com.springbloom.domain.model.FlowerOrder;
import com.springbloom.domain.model.OrderItem;
import com.springbloom.domain.model.OrderItemSpecies;
import com.springbloom.domain.model.vo.Money;

/** Both directions for the order aggregate. toDomain walks lazy collections: call it inside a transaction. */
public class FlowerOrderMapper {

    private FlowerOrderMapper() {
    }

    public static FlowerOrderEntity toEntity(FlowerOrder order) {
        FlowerOrderEntity entity = new FlowerOrderEntity();
        entity.setId(order.id());
        entity.setOrderNumber(order.orderNumber());
        entity.setQuotationId(order.quotationId());
        entity.setCustomerId(order.customerId());
        entity.setStatus(order.status());
        entity.setSubtotal(order.subtotal().amount());
        entity.setDiscountAmount(order.discountAmount().amount());
        entity.setTotalAmount(order.totalAmount().amount());

        for (OrderItem item : order.items()) {
            entity.addItem(toEntity(item));
        }
        return entity;
    }

    private static OrderItemEntity toEntity(OrderItem item) {
        OrderItemEntity entity = new OrderItemEntity();
        entity.setProductType(item.productType());
        entity.setDiscountPercentage(item.discountPercentage());
        entity.setSubtotal(item.subtotal().amount());

        for (OrderItemSpecies line : item.species()) {
            OrderItemSpeciesEntity row = new OrderItemSpeciesEntity();
            row.setSpeciesId(line.speciesId());
            row.setCommonNameSnapshot(line.commonNameSnapshot());
            row.setQuantity(line.quantity());
            row.setUnitPriceSnapshot(line.unitPriceSnapshot().amount());
            row.setLineTotal(line.lineTotal().amount());
            entity.addSpecies(row);
        }
        return entity;
    }

    public static FlowerOrder toDomain(FlowerOrderEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(FlowerOrderMapper::toDomain)
                .toList();

        return new FlowerOrder(
                entity.getId(),
                entity.getOrderNumber(),
                entity.getQuotationId(),
                entity.getCustomerId(),
                entity.getStatus(),
                items,
                Money.of(entity.getSubtotal()),
                Money.of(entity.getDiscountAmount()),
                Money.of(entity.getTotalAmount()),
                entity.getCreatedAt());
    }

    private static OrderItem toDomain(OrderItemEntity entity) {
        List<OrderItemSpecies> species = entity.getSpecies().stream()
                .map(row -> new OrderItemSpecies(
                        row.getSpeciesId(),
                        row.getCommonNameSnapshot(),
                        row.getQuantity(),
                        Money.of(row.getUnitPriceSnapshot()),
                        Money.of(row.getLineTotal())))
                .toList();

        return new OrderItem(
                entity.getProductType(),
                entity.getDiscountPercentage(),
                species,
                Money.of(entity.getSubtotal()));
    }
}
