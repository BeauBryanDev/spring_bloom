package com.springbloom.domain.model;

import com.springbloom.domain.service.ColombianPhone;

/**
 * Who an order is for and where it goes. Email is optional; the other three are
 * not.
 */
public record Customer(
        Long id,
        String fullName,
        String email,
        String phone,
        String deliveryAddress) {

    public Customer {
        fullName = required(fullName, "El nombre");
        phone = required(phone, "El telefono");
        deliveryAddress = required(deliveryAddress, "La direccion de entrega");
        email = email == null || email.isBlank() ? null : email.trim();
    }

    /**
     * A new customer must have a Colombian mobile number (ten digits, starting
     * with 3), stored as plain digits. Only creation is strict: the constructor
     * stays lenient so rows saved before this rule can still be read.
     */
    public static Customer unsaved(String fullName,
            String email,
            String phone,
            String address) {

        String mobile = ColombianPhone.normalize(phone).orElseThrow(() ->
                new IllegalArgumentException(
                        "El telefono debe ser un celular colombiano de 10 digitos que empiece por 3"));

        return new Customer(null, fullName,
                email,
                mobile,
                address);
    }

    public Customer withId(Long assignedId) {

        return new Customer(assignedId,
                fullName,
                email,
                phone,
                deliveryAddress);
    }

    private static String required(String value, String label) {

        if (value == null || value.isBlank()) {

            throw new IllegalArgumentException(label + " es obligatorio");
        }
        return value.trim();
    }
}
