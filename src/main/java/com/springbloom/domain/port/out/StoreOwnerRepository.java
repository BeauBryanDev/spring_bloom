package com.springbloom.domain.port.out;

import java.util.Optional;

import com.springbloom.domain.model.StoreOwner;

/** The admin login identity. minimal data, no password. */
public interface StoreOwnerRepository {

    Optional<StoreOwner> findByEmail(String email);
}
