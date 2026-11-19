package com.springbloom.domain.model;

import java.time.Instant;

import com.springbloom.domain.model.vo.Money;

/**
 * One payment attempt. Holds provider references only, never card data: that is
 * what keeps this codebase out of PCI scope.
 */
public record Payment(
        Long id,
        String paymentReference,
        Long orderId,
        String providerSessionId,
        String providerPaymentIntent,
        PaymentStatus status,
        Money amount,
        String currency,
        String failureReason,
        Instant paidAt) {

    public static Payment pending(
            String paymentReference,
            Long orderId, String sessionId,
            Money amount,
            String currency) {

        return new Payment(null, paymentReference,
                orderId,
                sessionId,
                null,
                PaymentStatus.PENDING,
                amount,
                currency,
                null, null);
    }

    public boolean isPaid() {
        return status == PaymentStatus.PAID;
    }
}
