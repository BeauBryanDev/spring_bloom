package com.springbloom.domain.port.out;

import com.springbloom.domain.model.Customer;

public interface CustomerRepository {

    /** Inserts a new customer and returns it with its id. */
    Customer save(Customer customer);
}
