package com.springbloom.application.usecase;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.springbloom.domain.model.Creation;
import com.springbloom.domain.port.in.ListCreationsUseCase;
import com.springbloom.domain.port.out.CreationRepository;
import com.springbloom.domain.service.CreationSearch;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ListCreationsService implements ListCreationsUseCase {

    private final CreationRepository creations;

    @Override
    public List<Creation> listActive() {
        return creations.findAll().stream().filter(Creation::active).toList();
    }

    @Override
    public Optional<Creation> findActive(String creationKey) {
        return creations.findByKey(creationKey).filter(Creation::active);
    }

    @Override
    public List<Creation> search(String text) {
        return CreationSearch.search(text, listActive());
    }
}
