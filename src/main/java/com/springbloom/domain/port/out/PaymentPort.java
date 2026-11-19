package com.springbloom.domain.port.out;

import com.springbloom.domain.model.vo.Money;

/**
 * The payment provider, as the domain sees it. No provider type crosses this
 * line, so swapping Stripe for another acquirer replaces one adapter.
 */
public interface PaymentPort {

    /** @throws com.springbloom.domain.service.PaymentNotConfiguredException when no provider key is set */
    CheckoutSession createCheckout(CheckoutRequest request);

    /**
     * Verifies the provider's signature and reads the event.
     *
     * @throws com.springbloom.domain.service.InvalidPaymentEventException on a bad signature or payload
     */
    PaymentEvent readNotification(String payload, String signature);

    record CheckoutRequest(String orderNumber, String paymentReference, Money amount,
            String currency, String description) {
    }

    record CheckoutSession(String sessionId, String redirectUrl) {
    }

    enum Kind { PAID, EXPIRED, IGNORED }

    record PaymentEvent(Kind kind, String sessionId, String paymentIntentId) {

        public static PaymentEvent ignored() {
            return new PaymentEvent(Kind.IGNORED, null, null);
        }
    }
}
