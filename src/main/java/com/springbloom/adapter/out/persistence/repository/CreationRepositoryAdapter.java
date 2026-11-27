package com.springbloom.adapter.out.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.springbloom.adapter.out.persistence.entity.CreationEntity;
import com.springbloom.domain.model.Creation;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.out.CreationRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreationRepositoryAdapter implements CreationRepository {

    private final CreationJpaRepository jpaRepository;

    @Override
    public List<Creation> findAll() {
        return jpaRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(CreationRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<Creation> findByKey(String creationKey) {
        if (creationKey == null || creationKey.isBlank()) {
            return Optional.empty();
        }
        return jpaRepository.findByCreationKey(creationKey).map(CreationRepositoryAdapter::toDomain);
    }

    private static Creation toDomain(CreationEntity entity) {
        List<Creation.Ingredient> ingredients = entity.getIngredients().stream()
                .map(row -> new Creation.Ingredient(
                        row.getSpecies().getSpeciesKey(),
                        row.getSpecies().getCommonName()))
                .toList();

        return new Creation(
                entity.getId(),
                entity.getCreationKey(),
                entity.getName(),
                entity.getProductType(),
                Money.of(entity.getPrice()),
                entity.getImageFile(),
                entity.getDescription(),
                entity.getDisplayOrder(),
                entity.isActive(),
                ingredients);
    }
}
