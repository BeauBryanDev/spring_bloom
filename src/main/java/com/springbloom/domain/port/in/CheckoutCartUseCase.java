package com.springbloom.domain.port.in;

import java.util.List;

/**
 * Pays a cart directly: the cart becomes an ACCEPTED quotation and a PENDING
 * order in one step, and the customer is sent to the hosted checkout. Nothing is
 * marked paid here; that is still only the signed webhook.
 */
public interface CheckoutCartUseCase {

    /**
     * @throws IllegalArgumentException with a Spanish reason for missing or invalid customer data
     * @throws com.springbloom.domain.service.UnavailableSpeciesException if a flower cannot be sold in
     *         the amount asked
     * @throws com.springbloom.domain.service.UnknownSpeciesException if a key is not in the catalog
     * @throws com.springbloom.domain.service.UnavailableCreationException if an arrangement is unknown or switched off
     */
    CartCheckout checkout(CheckoutCartCommand command);

    record CartLine(String speciesKey, int quantity) {

        public CartLine {
            if (speciesKey == null || speciesKey.isBlank()) {
                throw new IllegalArgumentException("speciesKey is required");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive: " + quantity);
            }
        }
    }

    record ArrangementLine(String creationKey, int units) {

        public ArrangementLine {
            if (creationKey == null || creationKey.isBlank()) {
                throw new IllegalArgumentException("creationKey is required");
            }
            if (units <= 0) {
                throw new IllegalArgumentException("units must be positive: " + units);
            }
        }
    }

    record CheckoutCartCommand(
            List<CartLine> lines,
            List<ArrangementLine> arrangements,
            String fullName,
            String phone,
            String deliveryAddress,
            String email) {

        public CheckoutCartCommand {
            lines = lines == null ? List.of() : List.copyOf(lines);
            arrangements = arrangements == null ? List.of() : List.copyOf(arrangements);
            if (lines.isEmpty() && arrangements.isEmpty()) {
                throw new IllegalArgumentException("El carrito esta vacio");
            }
        }

        public CheckoutCartCommand(List<CartLine> lines, 
                                    String fullName, 
                                    String phone,
                                    String deliveryAddress, 
                                    String email) {
                                        
            this(lines, List.of(), fullName, phone, deliveryAddress, email);
        }
    }

    /** paymentUrl is null when the order exists but the checkout could not be opened. */
    record CartCheckout(String orderNumber,
                        String quotationNumber,
                        String paymentUrl,
                        String paymentProblem) {
    }
}
