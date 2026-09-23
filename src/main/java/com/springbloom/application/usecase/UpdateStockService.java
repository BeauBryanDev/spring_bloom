package com.springbloom.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.in.UpdateStockUseCase;
import com.springbloom.domain.port.out.FlowerStockRepository;

/** The domain constructor re-validates every invariant, so a bad edit fails before it is saved. */
@Service
public class UpdateStockService implements UpdateStockUseCase {

    private final FlowerStockRepository stockRepository;

    public UpdateStockService(FlowerStockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Override
    @Transactional
    public FlowerStock update(UpdateStockCommand command) {
        FlowerStock existing = stockRepository.findBySpeciesKey(command.speciesKey())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No flower_stock row for species_key " + command.speciesKey()));

        FlowerStock updated = new FlowerStock(
                existing.getId(),
                existing.getSpeciesId(),
                command.status(),
                command.quantity(),
                command.etaDays(),
                Money.of(command.basePrice()),
                command.importPriceMultiplier(),
                existing.getUpdatedAt());

        return stockRepository.save(updated);
    }
}
