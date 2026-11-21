package com.springbloom.application.usecase;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springbloom.domain.model.AdminOrderDetail;
import com.springbloom.domain.port.in.AdminOrderDetailUseCase;
import com.springbloom.domain.port.out.CustomerRepository;
import com.springbloom.domain.port.out.FlowerOrderRepository;
import com.springbloom.domain.port.out.PaymentRepository;
import com.springbloom.domain.port.out.ShipmentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminOrderDetailService implements AdminOrderDetailUseCase {

    private final FlowerOrderRepository orders;
    private final CustomerRepository customers;
    private final ShipmentRepository shipments;
    private final PaymentRepository payments;

    @Override
    @Transactional(readOnly = true)
    public Optional<AdminOrderDetail> findByNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) {
            return Optional.empty();
        }
        // The order number is case-insensitive and whitespace-insensitive, so we normalize it before searching.
        return orders.findByNumber(orderNumber.trim().toUpperCase()).flatMap(order ->
                customers.findById(order.customerId()).map(customer -> new AdminOrderDetail(
                        order,
                        customer,
                        shipments.findByOrderId(order.id()),
                        payments.findByOrderId(order.id()))
                    ));
    }
}
