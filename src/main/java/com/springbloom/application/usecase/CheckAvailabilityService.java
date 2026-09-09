package com.springbloom.application.usecase;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.port.in.CheckAvailabilityUseCase;
import com.springbloom.domain.port.out.FlowerCatalogPort;
import com.springbloom.domain.port.out.FlowerSpeciesRepository;
import com.springbloom.domain.port.out.FlowerStockRepository;
import com.springbloom.domain.service.SpeciesSearch;

import lombok.RequiredArgsConstructor;

/**
 * Matching happens in the catalog, pricing in the database - the same split the
 * vision path uses. The catalog is already in memory and holds identity only, so
 * a search costs nothing until there is a species worth pricing.
 */
@Service
@RequiredArgsConstructor
public class CheckAvailabilityService implements CheckAvailabilityUseCase {

    private static final Logger log = LoggerFactory.getLogger(CheckAvailabilityService.class);

    private final FlowerCatalogPort catalog;
    private final FlowerSpeciesRepository speciesRepository;
    private final FlowerStockRepository stockRepository;

    @Override
    public List<AvailableFlower> check(CheckAvailabilityCommand command) {
        List<AvailableFlower> answers = SpeciesSearch
                .match(catalog.all(), command.term(), command.limit())
                .stream()
                .map(match -> price(match.species().getSpeciesKey(), command))
                .flatMap(Optional::stream)
                .toList();

        if (answers.isEmpty()) {
            log.debug("Nothing in the catalog resembles '{}'", command.term());
        }
        return answers;
    }

    /**
     * The catalog species carries no id and no price - it comes from flowers.json.
     * The persisted row is what has both, so every match is resolved through the
     * repository before it can be quoted.
     */
    private Optional<AvailableFlower> price(String speciesKey, CheckAvailabilityCommand command) {
        Optional<FlowerSpecies> persisted = speciesRepository.findBySpeciesKey(speciesKey);
        if (persisted.isEmpty()) {
            log.warn("Catalog knows {} but no such species is persisted", speciesKey);
            return Optional.empty();
        }

        Optional<FlowerStock> stock = stockRepository.findBySpeciesKey(speciesKey);
        if (stock.isEmpty()) {
            log.warn("Species {} has no flower_stock row, leaving it out of the answer", speciesKey);
            return Optional.empty();
        }

        return Optional.of(new AvailableFlower(
                persisted.get(), stock.get(), fulfils(stock.get(), command)));
    }

    /** With no quantity asked, "can we fulfil it" is just "can we sell it at all". */
    private boolean fulfils(FlowerStock stock, CheckAvailabilityCommand command) {
        return command.requestedQuantity()
                .map(stock::canFulfil)
                .orElseGet(stock::sellable);
    }
}
