


package com.springbloom.domain.port.out;

import java.util.List;

import com.springbloom.domain.model.FlowerOrderSummary;

/**
 * Read-only for now. The write side - ConfirmOrderUseCase, OrderItem,
 * OrderItemSpecies, the stock decrement on confirmation - does not exist yet;
 */
public interface FlowerOrderRepository {

    List<FlowerOrderSummary> findAllSummaries();
}
