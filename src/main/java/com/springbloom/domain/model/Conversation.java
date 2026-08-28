package com.springbloom.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * An anonymous chat session. The customer is identified by a session key from
 * the browser, not by a login: customerId stays null until there is enough
 * information to raise a quotation for a real person.
 *
 * The message list is whatever history was loaded, which is usually a bounded
 * tail rather than everything ever said. Do not treat its size as a turn count.
 */
public record Conversation(
        UUID id,
        Long customerId,
        String sessionKey,
        List<Message> messages,
        Instant createdAt) {

    public Conversation {

        if (sessionKey == null || sessionKey.isBlank()) {

            throw new IllegalArgumentException("A session key is required");
        }
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    /** A conversation the repository has not written yet: it assigns the id. */
    public static Conversation startedBy(String sessionKey) {

        return new Conversation(null, null, sessionKey, List.of(), null);
    }

    public boolean persisted() {

        return id != null;
    }

    /** Null until the chat has produced a customer, which a quotation may. */
    public Optional<Long> customer() {

        return Optional.ofNullable(customerId);
    }

    public Conversation withCustomer(Long customer) {

        return new Conversation(id, customer, sessionKey, messages, createdAt);
    }

    public Conversation withMessages(List<Message> loaded) {

        return new Conversation(id, customerId, sessionKey, loaded, createdAt);
    }

    /** In-memory append, for building a prompt. Persisting is the repository's job. */
    public Conversation plus(Message message) {

        List<Message> grown = new ArrayList<>(messages);
        grown.add(message);
        return new Conversation(id, customerId, sessionKey, grown, createdAt);
    }

    /** What the agent replays as history: the prompt scaffolding is not part of it. */
    public List<Message> customerVisibleMessages() {

        return messages.stream()
                .filter(message -> message.role().visibleToCustomer())
                .toList();
    }

    public Optional<Message> lastMessage() {
        
        return messages.isEmpty()
                ? Optional.empty()
                : Optional.of(messages.get(messages.size() - 1));
    }
}
