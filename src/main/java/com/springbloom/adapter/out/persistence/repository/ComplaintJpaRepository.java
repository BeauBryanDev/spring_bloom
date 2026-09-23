package com.springbloom.adapter.out.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springbloom.adapter.out.persistence.entity.ComplaintEntity;

public interface ComplaintJpaRepository extends JpaRepository<ComplaintEntity, Long> {

    Optional<ComplaintEntity> findByComplaintNumber(String complaintNumber);

    List<ComplaintEntity> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    List<ComplaintEntity> findByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    List<ComplaintEntity> findAllByOrderByCreatedAtDesc();

    /**
     * Native, because flower_order has no entity yet: mapping the whole order
     * aggregate to read one id would be a large detour for a claim that only
     * needs a foreign key. Replace with a derived query once it exists.
     */
    @Query(value = "SELECT id FROM flower_order WHERE order_number = :orderNumber",
            nativeQuery = true)
    Optional<Long> findOrderIdByNumber(@Param("orderNumber") String orderNumber);
}
