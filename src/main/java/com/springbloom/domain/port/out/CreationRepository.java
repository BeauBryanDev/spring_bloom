package com.springbloom.domain.port.out;

import java.util.List;
import java.util.Optional;

import com.springbloom.domain.model.Creation;

/** The garlands and crowns, active or not, in display order. */
public interface CreationRepository {

    List<Creation> findAll();

    Optional<Creation> findByKey(String creationKey);
}
