package com.springbloom.adapter.out.persistence.repository;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.springbloom.adapter.out.persistence.entity.CustomerEntity;
import com.springbloom.domain.model.Customer;
import com.springbloom.domain.port.out.CustomerRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CustomerRepositoryAdapter implements CustomerRepository {

    private final CustomerJpaRepository jpaRepository;

    @Override
    @Transactional
    public Customer save(Customer customer) {
        
        CustomerEntity entity = new CustomerEntity();
        entity.setFullName(customer.fullName());
        entity.setEmail(customer.email());
        entity.setPhone(customer.phone());
        entity.setDeliveryAddress(customer.deliveryAddress());

        return customer.withId(jpaRepository.saveAndFlush(entity).getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> findById(Long id) {

        return jpaRepository.findById(id).map(entity -> new Customer(entity.getId(),
                entity.getFullName(), 
                entity.getEmail(), 
                entity.getPhone(),
                entity.getDeliveryAddress()));
    }
}
