package com.springbloom.adapter.out.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springbloom.adapter.out.persistence.entity.PaymentEntity;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {

    Optional<PaymentEntity> findByProviderSessionId(String providerSessionId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM payment WHERE order_id = :orderId AND status = 'PAID')",
            nativeQuery = true)
    boolean existsPaid(@Param("orderId") Long orderId);
}
