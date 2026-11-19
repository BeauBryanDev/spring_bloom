package com.springbloom.domain.port.in;

import java.util.List;
import java.util.Optional;

import com.springbloom.domain.model.OrderStatus;
import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.vo.Money;

/**
 * The customer's side of an order: look it up by its number, then pay it. The
 * view carries no name, phone or address, because an order number is not a
 * secret and anyone holding one can open this.
 */
public interface PayOrderUseCase {

    Optional<OrderView> view(String orderNumber);

    /**
     * @throws IllegalArgumentException if the order does not exist
     * @throws IllegalStateException if it is not payable (already paid, or not pending)
     * @throws com.springbloom.domain.service.PaymentNotConfiguredException if payments are not set up
     */
    CheckoutLink startCheckout(String orderNumber);

    record CheckoutLink(String url) {
    }

    record OrderView(
            String orderNumber,
            OrderStatus status,
            boolean paid,
            boolean payable,
            List<ViewLine> lines,
            Money subtotal,
            Money discountAmount,
            Money total) {
    }

    record ViewLine(ProductType productType, String name, int quantity, Money unitPrice, Money lineTotal) {
    }
}
