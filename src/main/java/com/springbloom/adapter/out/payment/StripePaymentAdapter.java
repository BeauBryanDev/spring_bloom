package com.springbloom.adapter.out.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.springbloom.domain.port.out.PaymentPort;
import com.springbloom.domain.service.InvalidPaymentEventException;
import com.springbloom.domain.service.PaymentNotConfiguredException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;

/**
 * Stripe Checkout, hosted: the customer types the card on Stripe's page, so no
 * card number ever reaches this application. Sandbox keys only for this demo.
 *
 * With no secret key configured the adapter still loads and every checkout is
 * refused with PaymentNotConfiguredException, so the app boots before a Stripe
 * account exists.
 */
@Component
public class StripePaymentAdapter implements PaymentPort {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentAdapter.class);

    private final String secretKey;
    private final String webhookSecret;
    private final String baseUrl;

    public StripePaymentAdapter(
            @Value("${florabelle.stripe.secret-key:}") String secretKey,
            @Value("${florabelle.stripe.webhook-secret:}") String webhookSecret,
            @Value("${florabelle.public-base-url:http://localhost:8080}") String baseUrl) {

        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public CheckoutSession createCheckout(CheckoutRequest request) {
        if (secretKey.isBlank()) {
            throw new PaymentNotConfiguredException();
        }

        String order = request.orderNumber();
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setClientReferenceId(order)
                .putMetadata("orderNumber", order)
                .putMetadata("paymentReference", request.paymentReference())
                .setSuccessUrl(baseUrl + "/carrito?orden=" + order + "&pago=exitoso")
                .setCancelUrl(baseUrl + "/carrito?orden=" + order + "&pago=cancelado")
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(request.currency().toLowerCase())
                                .setUnitAmount(minorUnits(request))
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(request.description())
                                        .build())
                                .build())
                        .build())
                .build();

        try {
            Session session = Session.create(params, RequestOptions.builder().setApiKey(secretKey).build());
            return new CheckoutSession(session.getId(), session.getUrl());
        } catch (StripeException failure) {
            log.error("Stripe refused the checkout for {}: {}", order, failure.getMessage());
            throw new IllegalStateException("No pudimos iniciar el pago. Intente de nuevo en un momento", failure);
        }
    }

    /** COP is a two-decimal currency at Stripe, so pesos are sent as centavos. */
    private static long minorUnits(CheckoutRequest request) {
        return request.amount().amount().movePointRight(2).longValueExact();
    }

    @Override
    public PaymentEvent readNotification(String payload, String signature) {
        if (webhookSecret.isBlank()) {
            throw new InvalidPaymentEventException("No webhook secret configured", null);
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException | RuntimeException invalid) {
            throw new InvalidPaymentEventException("Bad signature or payload", invalid);
        }

        return switch (event.getType()) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> {
                Session session = session(event);
                // A completed session can still be unpaid (delayed methods); only "paid" is money.
                yield "paid".equals(session.getPaymentStatus())
                        ? new PaymentEvent(Kind.PAID, session.getId(), session.getPaymentIntent())
                        : PaymentEvent.ignored();
            }
            case "checkout.session.expired" ->
                new PaymentEvent(Kind.EXPIRED, session(event).getId(), null);
            default -> PaymentEvent.ignored();
        };
    }

    private static Session session(Event event) {
        StripeObject object = event.getDataObjectDeserializer().getObject()
                .orElseGet(() -> {
                    try {
                        return event.getDataObjectDeserializer().deserializeUnsafe();
                    } catch (Exception failure) {
                        throw new InvalidPaymentEventException("Unreadable event body", failure);
                    }
                });
        return (Session) object;
    }
}
