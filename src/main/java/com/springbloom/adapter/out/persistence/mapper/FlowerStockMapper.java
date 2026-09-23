package com.springbloom.adapter.out.persistence.mapper;

import com.springbloom.adapter.out.persistence.entity.FlowerStockEntity;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.vo.Money;

public class FlowerStockMapper {

    private FlowerStockMapper() {
    }

    public static FlowerStock toDomain(FlowerStockEntity entity) {
        return new FlowerStock(
                entity.getId(),
                entity.getSpeciesId(),
                entity.getStatus(),
                entity.getStockQuantity(),
                entity.getEtaDays(),
                Money.of(entity.getBasePrice()),
                entity.getImportPriceMultiplier(),
                entity.getUpdatedAt()
        );
    }
}
