package com.springbloom.adapter.out.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.springbloom.adapter.out.persistence.entity.PaymentEntity;
import com.springbloom.domain.model.Payment;
import com.springbloom.domain.model.PaymentStatus;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.out.PaymentRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentRepositoryAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    @Override
    @Transactional
    public Payment save(Payment payment) {

        PaymentEntity entity = new PaymentEntity();

        entity.setPaymentReference(payment.paymentReference());
        entity.setOrderId(payment.orderId());
        entity.setProviderSessionId(payment.providerSessionId());
        entity.setStatus(payment.status());
        entity.setAmount(payment.amount().amount());
        entity.setCurrency(payment.currency());

        return toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<Payment> findBySessionId(String providerSessionId) {
        
        return jpaRepository.findByProviderSessionId(providerSessionId).map(PaymentRepositoryAdapter::toDomain);
    }

    @Override
    public boolean existsPaidForOrder(Long orderId) {

        return jpaRepository.existsPaid(orderId);
    }

    @Override
    public List<Payment> findByOrderId(Long orderId) {

        return jpaRepository.findByOrderIdOrderByCreatedAtDescIdDesc(orderId).stream()
                .map(PaymentRepositoryAdapter::toDomain)
                .toList();
    }

    /** paid_at is written here and nowhere else: ck_payment_paid_has_date pairs it with PAID. */
    @Override
    @Transactional
    public Payment markPaid(Long paymentId, 
        String paymentIntent, 
        String note, 
        Instant paidAt) {

        PaymentEntity entity = load(paymentId);
        entity.setStatus(PaymentStatus.PAID);
        entity.setProviderPaymentIntent(paymentIntent);
        entity.setFailureReason(note);
        entity.setPaidAt(paidAt);
        
        return toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    @Transactional
    public void markEnded(Long paymentId, PaymentStatus status) {
        PaymentEntity entity = load(paymentId);
        entity.setStatus(status);
        jpaRepository.saveAndFlush(entity);
    }

    private PaymentEntity load(Long paymentId) {
        return jpaRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("No payment with id " + paymentId));
    }

    private static Payment toDomain(PaymentEntity entity) {
        return new Payment(
                entity.getId(),
                entity.getPaymentReference(),
                entity.getOrderId(),
                entity.getProviderSessionId(),
                entity.getProviderPaymentIntent(),
                entity.getStatus(),
                Money.of(entity.getAmount()),
                entity.getCurrency(),
                entity.getFailureReason(),
                entity.getPaidAt());
    }
}
