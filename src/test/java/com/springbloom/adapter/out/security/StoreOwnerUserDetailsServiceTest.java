package com.springbloom.adapter.out.security;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.springbloom.domain.model.StoreOwner;
import com.springbloom.domain.port.out.StoreOwnerRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoreOwnerUserDetailsServiceTest {

    @Test
    @DisplayName("the owner's email, hash and role become the login identity")
    void loadsOwner() {
        StoreOwnerRepository owners = mock(StoreOwnerRepository.class);
        when(owners.findByEmail("admin@florabelle.co")).thenReturn(Optional.of(
                new StoreOwner(1L, "admin@florabelle.co", "$2a$10$hash", "Admin", "ADMIN", Instant.now())));

        UserDetails user = new StoreOwnerUserDetailsService(owners).loadUserByUsername("admin@florabelle.co");

        assertThat(user.getUsername()).isEqualTo("admin@florabelle.co");
        assertThat(user.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(user.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("an unknown email is UsernameNotFoundException, not a null user")
    void unknown() {
        StoreOwnerRepository owners = mock(StoreOwnerRepository.class);
        when(owners.findByEmail("x@y.z")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new StoreOwnerUserDetailsService(owners).loadUserByUsername("x@y.z"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
