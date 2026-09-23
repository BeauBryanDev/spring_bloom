package com.springbloom.adapter.out.persistence.repository;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.springbloom.adapter.out.persistence.mapper.StoreOwnerMapper;
import com.springbloom.domain.model.StoreOwner;
import com.springbloom.domain.port.out.StoreOwnerRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreOwnerRepositoryAdapter implements StoreOwnerRepository {

    private final StoreOwnerJpaRepository jpaRepository;

    @Override
    public Optional<StoreOwner> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(StoreOwnerMapper::toDomain);
    }
}
