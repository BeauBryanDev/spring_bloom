package com.springbloom.application.usecase;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.port.in.ListCatalogUseCase;
import com.springbloom.domain.port.out.FlowerSpeciesRepository;
import com.springbloom.domain.port.out.FlowerStockRepository;

/**
 * Joins the species catalog to its stock rows in memory. There are 90 species,
 * so two full reads cost less than a join the repositories do not expose, and
 * the split keeps identity and commercial state in their own ports.
 */
@Service
public class ListCatalogService implements ListCatalogUseCase {

    private final FlowerSpeciesRepository speciesRepository;
    private final FlowerStockRepository stockRepository;

    public ListCatalogService(
            FlowerSpeciesRepository speciesRepository,
            FlowerStockRepository stockRepository) {
        this.speciesRepository = speciesRepository;
        this.stockRepository = stockRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogEntry> listSellable() {
        return entries()
                .filter(entry -> entry.stock().sellable())
                .sorted(displayOrder())
                .toList();
    }

    /** A species with no flower_stock row has no price and nothing to sell: it is skipped. */
    private Stream<CatalogEntry> entries() {
        Map<Long, FlowerStock> stockBySpeciesId = stockRepository.findAll().stream()
                .collect(Collectors.toMap(FlowerStock::getSpeciesId, Function.identity()));

        return speciesRepository.findAll().stream()
                .filter(species -> stockBySpeciesId.containsKey(species.getId()))
                .map(species -> new CatalogEntry(species, stockBySpeciesId.get(species.getId())));
    }

    /**
     * Keyed lookup, not a scan of listAll(): the product page needs one species
     * and species_key is UNIQUE. Matching is exact and case sensitive, like
     * everywhere else this key is used.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogEntry> findByKey(String speciesKey) {
        if (speciesKey == null || speciesKey.isBlank()) {
            return Optional.empty();
        }
        return speciesRepository.findBySpeciesKey(speciesKey)
                .flatMap(species -> stockRepository.findBySpeciesKey(speciesKey)
                        .map(stock -> new CatalogEntry(species, stock)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogEntry> listAll() {
        return entries().sorted(displayOrder()).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogEntry> listFeatured(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }
        return listSellable().stream().limit(limit).toList();
    }

    /** What is on hand first, then alphabetically, so the shelf reads the same every visit. */
    private static Comparator<CatalogEntry> displayOrder() {
        return Comparator
                .comparing((CatalogEntry entry) -> !entry.availableImmediately())
                .thenComparing(entry -> entry.stock().getStatus().ordinal())
                .thenComparing(CatalogEntry::commonName, String.CASE_INSENSITIVE_ORDER);
    }
}
