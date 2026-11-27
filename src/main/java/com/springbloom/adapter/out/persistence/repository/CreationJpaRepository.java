package com.springbloom.adapter.out.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.springbloom.adapter.out.persistence.entity.CreationEntity;

public interface CreationJpaRepository extends JpaRepository<CreationEntity, Long> {

    @EntityGraph(attributePaths = {"ingredients", "ingredients.species"})
    List<CreationEntity> findAllByOrderByDisplayOrderAscIdAsc();

    @EntityGraph(attributePaths = {"ingredients", "ingredients.species"})
    Optional<CreationEntity> findByCreationKey(String creationKey);
}
