package com.springbloom.adapter.in.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springbloom.domain.port.in.ConfirmOrderUseCase;
import com.springbloom.domain.service.InvalidPaymentEventException;

import lombok.RequiredArgsConstructor;

/**
 * The only door through which an order becomes paid. The body is taken as a raw
 * String because the signature is computed over the exact bytes Stripe sent:
 * letting Jackson parse and re-serialize it first would break the check.
 *
 * Any failure other than a bad signature propagates as a 500 on purpose, so
 * Stripe retries the delivery.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final ConfirmOrderUseCase confirmOrder;

    @PostMapping("/stripe/webhook")
    public ResponseEntity<Void> receive(
            @RequestBody String payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature) {

        try {
            confirmOrder.handleNotification(payload, signature);
            return ResponseEntity.ok().build();
        } catch (InvalidPaymentEventException rejected) {
            log.warn("Rejected a payment notification: {}", rejected.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
