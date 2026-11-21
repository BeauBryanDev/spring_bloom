package com.springbloom.adapter.out.persistence.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.springbloom.domain.port.out.StoreOwnerRepository;

import static org.assertj.core.api.Assertions.assertThat;

/** Read-only: the seeded owner is the only row, and nothing here writes. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_USER", matches = ".+")
class StoreOwnerRepositoryAdapterTest {

    @Autowired
    private StoreOwnerRepository owners;

    @Test
    @DisplayName("the seeded owner loads with a BCrypt hash and the ADMIN role")
    void findsSeededOwner() {
        var owner = owners.findByEmail("admin@florabelle.co").orElseThrow();

        assertThat(owner.role()).isEqualTo("ADMIN");
        assertThat(owner.passwordHash()).startsWith("$2");
        assertThat(owner.fullName()).isNotBlank();
    }

    @Test
    @DisplayName("an unknown email is empty")
    void unknownEmail() {
        assertThat(owners.findByEmail("nobody@example.com")).isEmpty();
    }
}
