package com.springbloom.domain.port.out;

import java.util.List;
import java.util.Optional;

import com.springbloom.domain.model.FlowerStock;

/** Price and availability lookups. One flower_stock row exists per species. */
public interface FlowerStockRepository {

    Optional<FlowerStock> findBySpeciesId(Long speciesId);

    /** Resolves stock straight from the label the vision model emits. */
    Optional<FlowerStock> findBySpeciesKey(String speciesKey);

    List<FlowerStock> findAll();

    /** Updates an existing row. stock.getId() must be a real flower_stock_id. */
    FlowerStock save(FlowerStock stock);
}
