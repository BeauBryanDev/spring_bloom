package com.springbloom.adapter.out.persistence.repository;

import com.springbloom.adapter.out.persistence.mapper.FlowerSpeciesMapper;
import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.port.out.FlowerSpeciesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Backs the FlowerSpeciesRepository port with JPA, mapping entities to domain. */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlowerSpeciesRepositoryAdapter implements FlowerSpeciesRepository {

    private final FlowerSpeciesJpaRepository jpaRepository;

    @Override
    public List<FlowerSpecies> findAll() {
        return jpaRepository.findAll().stream()
                .map(FlowerSpeciesMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<FlowerSpecies> findBySpeciesKey(String speciesKey) {
        return jpaRepository.findBySpeciesKey(speciesKey)
                .map(FlowerSpeciesMapper::toDomain);
    }
}
