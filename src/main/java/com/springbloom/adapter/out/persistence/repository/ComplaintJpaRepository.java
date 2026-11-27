package com.springbloom.adapter.out.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springbloom.adapter.out.persistence.entity.ComplaintEntity;

public interface ComplaintJpaRepository extends JpaRepository<ComplaintEntity, Long> {

    Optional<ComplaintEntity> findByComplaintNumber(String complaintNumber);

    List<ComplaintEntity> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    List<ComplaintEntity> findByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    List<ComplaintEntity> findAllByOrderByCreatedAtDesc();
}
