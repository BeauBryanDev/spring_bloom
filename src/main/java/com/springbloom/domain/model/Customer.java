package com.springbloom.domain.model;

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

    public static Customer unsaved(String fullName,
            String email,
            String phone,
            String address) {

        return new Customer(null, fullName,
                email,
                phone,
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
