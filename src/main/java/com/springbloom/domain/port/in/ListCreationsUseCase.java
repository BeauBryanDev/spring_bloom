package com.springbloom.domain.port.in;

import java.util.List;
import java.util.Optional;

import com.springbloom.domain.model.Creation;

/** The garlands and crowns that can be sold now, for the storefront, the cart and the agent. */
public interface ListCreationsUseCase {

    List<Creation> listActive();

    Optional<Creation> findActive(String creationKey);

    /** Active arrangements matching free text; several means the customer must choose. */
    List<Creation> search(String text);
}
