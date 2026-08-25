package com.springbloom.adapter.out.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.model.FlowerStockStatus;
import com.springbloom.domain.port.out.FlowerCatalogPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory index of the 90 species the model can recognize, loaded once from
 * flowers.json. It answers "which species is this label", nothing more: price
 * and stock come from flower_stock through the repository, so no live
 * commercial state is duplicated here.
 */
@Component
public class FlowerCatalog implements FlowerCatalogPort {

    private static final String RESOURCE_PATH = "flowers.json";

    private final Map<String, FlowerSpecies> speciesByKey;
    private final Map<String, FlowerStockStatus> seedStatusByKey;

    public FlowerCatalog(ObjectMapper objectMapper) {
        List<Entry> entries = read(objectMapper);

        Map<String, FlowerSpecies> species = new LinkedHashMap<>();
        Map<String, FlowerStockStatus> statuses = new LinkedHashMap<>();
        for (Entry entry : entries) {
            FlowerSpecies previous = species.put(entry.modelClass(), entry.toSpecies());
            if (previous != null) {
                throw new IllegalStateException("Duplicate species key in catalog: " + entry.modelClass());
            }
            statuses.put(entry.modelClass(), entry.availability());
        }
        this.speciesByKey = Collections.unmodifiableMap(species);
        this.seedStatusByKey = Collections.unmodifiableMap(statuses);
    }

    private List<Entry> read(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<List<Entry>>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("Could not load flower catalog from " + RESOURCE_PATH, e);
        }
    }

    @Override
    public Optional<FlowerSpecies> find(String speciesKey) {
        return Optional.ofNullable(speciesByKey.get(speciesKey));
    }

    @Override
    public FlowerSpecies require(String speciesKey) {
        return find(speciesKey)
                .orElseThrow(() -> new IllegalArgumentException("Unknown flower class: " + speciesKey));
    }

    @Override
    public Optional<FlowerSpecies> resolveByIndex(int classIndex, String[] modelNames) {
        if (classIndex < 0 || classIndex >= modelNames.length) {
            return Optional.empty();
        }
        return find(modelNames[classIndex]);
    }

    @Override
    public Collection<FlowerSpecies> all() {
        return speciesByKey.values();
    }

    @Override
    public int size() {
        return speciesByKey.size();
    }

    /** Initial stock status for seeding flower_stock. Not the live status. */
    public Optional<FlowerStockStatus> seedStatus(String speciesKey) {
        return Optional.ofNullable(seedStatusByKey.get(speciesKey));
    }

    /**
     * One flowers.json record. modelClass is the label the model emits and is
     * stored as species_key; availability seeds flower_stock.status.
     */
    public record Entry(
            String modelClass,
            String commonName,
            String scientificName,
            FlowerStockStatus availability) {

        FlowerSpecies toSpecies() {
            return new FlowerSpecies(null, modelClass, commonName, scientificName, null, null, null);
        }
    }
}
