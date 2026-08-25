package com.springbloom.adapter.out.persistence.repository;

import com.springbloom.adapter.out.persistence.entity.FlowerSpeciesEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FlowerSpeciesJpaRepository extends JpaRepository<FlowerSpeciesEntity, Long> {

    Optional<FlowerSpeciesEntity> findBySpeciesKey(String speciesKey);
}
