package com.springbloom.domain.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.springbloom.domain.model.Payment;
import com.springbloom.domain.model.PaymentStatus;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findBySessionId(String providerSessionId);

    boolean existsPaidForOrder(Long orderId);

    /** Every attempt for the order, newest first. */
    List<Payment> findByOrderId(Long orderId);

    /** note may be null; it is stored in failure_reason when a paid attempt needs follow-up. */
    Payment markPaid(Long paymentId, String paymentIntent, 
                    String note, Instant paidAt);

    void markEnded(Long paymentId, PaymentStatus status);
}
