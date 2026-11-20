package com.springbloom.domain.port.out;

import java.util.Optional;

import com.springbloom.domain.model.Customer;

public interface CustomerRepository {

    /** Inserts a new customer and returns it with its id. */
    Customer save(Customer customer);
    // interface segregation. The port exposes only what the application uses.
    Optional<Customer> findById(Long id);
}
