package com.springbloom.adapter.out.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springbloom.adapter.out.persistence.entity.CustomerEntity;

public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, Long> {
}
