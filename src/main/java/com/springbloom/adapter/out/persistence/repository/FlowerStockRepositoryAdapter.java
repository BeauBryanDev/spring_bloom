package com.springbloom.adapter.out.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.springbloom.adapter.out.persistence.entity.FlowerStockEntity;
import com.springbloom.adapter.out.persistence.mapper.FlowerStockMapper;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.port.out.FlowerStockRepository;

import lombok.RequiredArgsConstructor;

/** Backs the FlowerStockRepository port with JPA, mapping entities to domain. */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlowerStockRepositoryAdapter implements FlowerStockRepository {

    private final FlowerStockJpaRepository jpaRepository;

    @Override
    public Optional<FlowerStock> findBySpeciesId(Long speciesId) {
        return jpaRepository.findBySpeciesId(speciesId)
                .map(FlowerStockMapper::toDomain);
    }

    @Override
    public Optional<FlowerStock> findBySpeciesKey(String speciesKey) {
        return jpaRepository.findBySpeciesKey(speciesKey)
                .map(FlowerStockMapper::toDomain);
    }

    @Override
    public List<FlowerStock> findAll() {
        return jpaRepository.findAll().stream()
                .map(FlowerStockMapper::toDomain)
                .toList();
    }

    /** Loads the row by id and mutates it in place, so updated_at's trigger fires on a real UPDATE. */
    @Override
    @Transactional
    public FlowerStock save(FlowerStock stock) {
        FlowerStockEntity entity = jpaRepository.findById(stock.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No flower_stock row with id " + stock.getId()));

        entity.setStatus(stock.getStatus());
        entity.setStockQuantity(stock.getQuantity());
        entity.setEtaDays(stock.getEtaDays());
        entity.setBasePrice(stock.getBasePrice().amount());
        entity.setImportPriceMultiplier(stock.getImportPriceMultiplier());

        return FlowerStockMapper.toDomain(jpaRepository.save(entity));
    }
}
