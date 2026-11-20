package com.springbloom.adapter.out.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springbloom.adapter.out.persistence.entity.ShipmentEntity;

public interface ShipmentJpaRepository extends JpaRepository<ShipmentEntity, Long> {

    Optional<ShipmentEntity> findByOrderId(Long orderId);
}
