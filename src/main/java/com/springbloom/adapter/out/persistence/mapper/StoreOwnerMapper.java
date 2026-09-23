package com.springbloom.adapter.out.persistence.mapper;

import com.springbloom.adapter.out.persistence.entity.StoreOwnerEntity;
import com.springbloom.domain.model.StoreOwner;

public class StoreOwnerMapper {

    private StoreOwnerMapper() {
    }

    public static StoreOwner toDomain(StoreOwnerEntity entity) {
        return new StoreOwner(
                entity.getId(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.getFullName(),
                entity.getRole(),
                entity.getCreatedAt());
    }
}
