package com.springbloom.domain.port.in;

/**
 * Settles an order once the payment provider says it was paid. This is the only
 * place stock comes down, and the only caller is the signed webhook: the
 * browser's return from checkout proves nothing and never reaches it.
 */
public interface ConfirmOrderUseCase {

    /**
     * Idempotent: the provider retries, so a payment already settled is a no-op.
     *
     * @throws com.springbloom.domain.service.InvalidPaymentEventException on a bad signature
     */
    void handleNotification(String payload, String signature);
}
