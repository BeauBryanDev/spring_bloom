package com.springbloom.domain.service;

/** A payment notification that failed its signature check or could not be read. */
public class InvalidPaymentEventException extends RuntimeException {

    public InvalidPaymentEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
