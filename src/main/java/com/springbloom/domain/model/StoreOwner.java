package com.springbloom.domain.model;

import java.time.Instant;
import java.util.Objects;

/** The shop's admin identity. The schema allows only role ADMIN today. */
public record StoreOwner(
        Long id,
        String email,
        String passwordHash,
        String fullName,
        String role,
        Instant createdAt) {

    public StoreOwner {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(fullName, "fullName");
        Objects.requireNonNull(role, "role");
    }
}
