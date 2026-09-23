package com.springbloom.adapter.out.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springbloom.adapter.out.persistence.entity.StoreOwnerEntity;

public interface StoreOwnerJpaRepository extends JpaRepository<StoreOwnerEntity, Long> {

    Optional<StoreOwnerEntity> findByEmail(String email);
}
