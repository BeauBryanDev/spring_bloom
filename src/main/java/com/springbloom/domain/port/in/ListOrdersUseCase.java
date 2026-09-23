package com.springbloom.domain.port.in;

import java.util.List;

import com.springbloom.domain.model.FlowerOrderSummary;

/** Read-only listing for the admin view. See FlowerOrderRepository for why. */
public interface ListOrdersUseCase {

    List<FlowerOrderSummary> listAll();
}
