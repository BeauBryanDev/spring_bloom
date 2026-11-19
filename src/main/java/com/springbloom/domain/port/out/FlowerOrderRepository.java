package com.springbloom.domain.port.out;

import java.util.List;
import java.util.Optional;

import com.springbloom.domain.model.FlowerOrder;
import com.springbloom.domain.model.FlowerOrderSummary;
import com.springbloom.domain.model.OrderStatus;

/**
 * Orders as whole documents. Numbering is the adapter's business, exactly as
 * for quotations: it retries on the UNIQUE collision the 5-digit suffix makes
 * routine.
 */
public interface FlowerOrderRepository {

    List<FlowerOrderSummary> findAllSummaries();

    FlowerOrder save(FlowerOrder order);

    Optional<FlowerOrder> findByNumber(String orderNumber);

    Optional<FlowerOrder> findById(Long id);

    /** At most one: flower_order.quotation_id is UNIQUE. */
    Optional<FlowerOrder> findByQuotationId(Long quotationId);

    void updateStatus(Long orderId, OrderStatus status);
}
