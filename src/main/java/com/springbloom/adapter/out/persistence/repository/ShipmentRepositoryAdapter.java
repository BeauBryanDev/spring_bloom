package com.springbloom.adapter.out.persistence.repository;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.springbloom.adapter.out.persistence.entity.ShipmentEntity;
import com.springbloom.adapter.out.persistence.mapper.ShipmentMapper;
import com.springbloom.domain.model.Shipment;
import com.springbloom.domain.port.out.ShipmentRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ShipmentRepositoryAdapter implements ShipmentRepository {

    private final ShipmentJpaRepository jpaRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Shipment> findByOrderId(Long orderId) {
        return jpaRepository.findByOrderId(orderId).map(ShipmentMapper::toDomain);
    }

    /** Loads and mutates in place so the updated_at trigger fires on a real UPDATE. */
    @Override
    @Transactional
    public Shipment save(Shipment shipment) {
        ShipmentEntity entity = shipment.id() == null
                ? new ShipmentEntity()
                : jpaRepository.findById(shipment.id()).orElseThrow(
                        () -> new IllegalArgumentException("No shipment with id " + shipment.id()));

        ShipmentMapper.copyInto(shipment, entity);
        return ShipmentMapper.toDomain(jpaRepository.saveAndFlush(entity));
    }
}
