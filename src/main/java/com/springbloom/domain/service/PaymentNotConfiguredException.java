package com.springbloom.domain.service;

/** Raised when a payment is asked for but no provider credentials are set. */
public class PaymentNotConfiguredException extends RuntimeException {

    public PaymentNotConfiguredException() {
        super("Los pagos en linea no estan disponibles en este momento");
    }
}
