package com.springbloom.adapter.out.persistence.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.springbloom.adapter.out.persistence.entity.ConversationEntity;
import com.springbloom.adapter.out.persistence.entity.MessageEntity;
import com.springbloom.adapter.out.persistence.mapper.ConversationMapper;
import com.springbloom.domain.model.Conversation;
import com.springbloom.domain.model.Message;
import com.springbloom.domain.model.MessageRole;
import com.springbloom.domain.port.out.ConversationRepository;

import jakarta.persistence.EntityManager;

/** Backs the ConversationRepository port with JPA. */
@Repository
@Transactional(readOnly = true)
public class ConversationRepositoryAdapter implements ConversationRepository {

    private static final Logger log = LoggerFactory.getLogger(ConversationRepositoryAdapter.class);

    private final ConversationJpaRepository conversations;
    private final MessageJpaRepository messages;
    private final EntityManager entityManager;
    private final TransactionTemplate insertTransaction;

    public ConversationRepositoryAdapter(
            ConversationJpaRepository conversations,
            MessageJpaRepository messages,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager) {

        this.conversations = conversations;
        this.messages = messages;
        this.entityManager = entityManager;
        this.insertTransaction = new TransactionTemplate(transactionManager);
        this.insertTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Read, then insert, then read again if the insert lost a race. Two requests
     * from the same browser can arrive together on the first turn, and the loser
     * must join the winner's conversation rather than fail: the session key is
     * the customer's identity, so a second conversation for it would strand the
     * history somewhere the next turn cannot find it.
     *
     * The insert runs in its own transaction so the constraint violation does
     * not poison the caller's, which would make the re-read fail too.
     */
    @Override
    public Conversation findOrStart(String sessionKey) {
        Optional<Conversation> existing = findBySessionKey(sessionKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            return insertTransaction.execute(status -> ConversationMapper.toDomain(
                    conversations.saveAndFlush(ConversationEntity.starting(sessionKey))));
        } catch (DataIntegrityViolationException collision) {
            log.debug("Session {} was started concurrently, joining it", sessionKey);
            return findBySessionKey(sessionKey).orElseThrow(() -> collision);
        }
    }

    @Override
    public Optional<Conversation> findBySessionKey(String sessionKey) {
        return conversations.findBySessionKey(sessionKey).map(ConversationMapper::toDomain);
    }

    @Override
    public Optional<Conversation> findById(UUID conversationId) {
        return conversations.findById(conversationId).map(ConversationMapper::toDomain);
    }

    /**
     * getReference sets the FK without loading the conversation: appending a turn
     * has no reason to read the row it points at.
     */
    @Override
    @Transactional
    public Message append(UUID conversationId, MessageRole role, String content) {
        MessageEntity entity = new MessageEntity();
        entity.setConversation(entityManager.getReference(ConversationEntity.class, conversationId));
        entity.setRole(role);
        entity.setContent(content);

        return ConversationMapper.toDomain(messages.saveAndFlush(entity));
    }

    /** Queried newest first so the limit takes the tail, then reversed to read forwards. */
    @Override
    public List<Message> history(UUID conversationId, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }

        List<Message> newestFirst = messages
                .findByConversationIdOrderByCreatedAtDescIdDesc(
                        conversationId, PageRequest.of(0, limit))
                .stream()
                .map(ConversationMapper::toDomain)
                .collect(Collectors.toCollection(ArrayList::new));

        Collections.reverse(newestFirst);
        return List.copyOf(newestFirst);
    }

    @Override
    @Transactional
    public Conversation assignCustomer(UUID conversationId, Long customerId) {
        ConversationEntity entity = conversations.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No such conversation: " + conversationId));

        entity.setCustomerId(customerId);
        return ConversationMapper.toDomain(conversations.saveAndFlush(entity));
    }
}
