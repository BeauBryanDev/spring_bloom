package com.springbloom.adapter.out.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.springbloom.adapter.out.persistence.entity.MessageEntity;

public interface MessageJpaRepository extends JpaRepository<MessageEntity, Long> {

    /**
     * Newest first with a Pageable limit, because the tail is what fits in a
     * context window. The adapter reverses the page so a prompt reads forwards.
     * Ordered by id as well as created_at: two turns in the same millisecond
     * would otherwise come back in an arbitrary order.
     */
    List<MessageEntity> findByConversationIdOrderByCreatedAtDescIdDesc(
            UUID conversationId, Pageable pageable);

    long countByConversationId(UUID conversationId);
}
