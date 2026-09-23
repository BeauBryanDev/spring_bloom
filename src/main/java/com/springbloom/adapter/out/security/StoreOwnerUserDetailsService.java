package com.springbloom.adapter.out.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.springbloom.domain.model.StoreOwner;
import com.springbloom.domain.port.out.StoreOwnerRepository;

import lombok.RequiredArgsConstructor;

/**
 * Backs form login with store_owner. The email is the username; role becomes
 * the Spring Security authority ROLE_ADMIN, the only role the schema allows.
 */
@Service
@RequiredArgsConstructor
public class StoreOwnerUserDetailsService implements UserDetailsService {

    private final StoreOwnerRepository storeOwnerRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        StoreOwner owner = storeOwnerRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No store owner with that email"));

        return User.builder()
                .username(owner.email())
                .password(owner.passwordHash())
                .roles(owner.role())
                .build();
    }
}
