package com.springbloom.adapter.out.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springbloom.adapter.out.persistence.entity.ConversationEntity;

public interface ConversationJpaRepository extends JpaRepository<ConversationEntity, UUID> {

    Optional<ConversationEntity> findBySessionKey(String sessionKey);
}
