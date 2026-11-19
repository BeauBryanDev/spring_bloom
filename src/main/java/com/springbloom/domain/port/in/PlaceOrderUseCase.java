package com.springbloom.domain.port.in;

import com.springbloom.domain.model.FlowerOrder;

/**
 * Turns a quotation the customer agreed to into a PENDING order. It takes no
 * payment and holds no stock: paying is what confirms an order.
 */
public interface PlaceOrderUseCase {

    /**
     * Placing the same quotation twice returns the order that already exists.
     *
     * @throws IllegalArgumentException with a Spanish reason when the quotation is unknown, not
     *         this session's, expired, or the customer data is incomplete
     * @throws com.springbloom.domain.service.UnavailableSpeciesException if stock no longer covers it
     */
    FlowerOrder place(PlaceOrderCommand command);

    record PlaceOrderCommand(
            String sessionKey,
            String quotationNumber,
            String fullName,
            String phone,
            String deliveryAddress,
            String email) {

        public PlaceOrderCommand {
            if (sessionKey == null || sessionKey.isBlank()) {
                throw new IllegalArgumentException("A session key is required");
            }
            if (quotationNumber == null || quotationNumber.isBlank()) {
                throw new IllegalArgumentException("El numero de cotizacion es obligatorio");
            }
        }
    }
}
