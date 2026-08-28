package com.springbloom.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One turn of a conversation. Content is stored verbatim: it is what the
 * customer actually said and what the agent actually answered, and a quotation
 * raised from it must be explainable afterwards.
 *
 * id and createdAt are null on a message that has not been appended yet.
 */
public record Message(
        Long id,
        UUID conversationId,
        MessageRole role,
        String content,
        Instant createdAt) {

    public Message {

        if (role == null) {
            throw new IllegalArgumentException("role is required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("A message needs content");
        }
    }

    /** An unsaved turn: the repository assigns the id and the timestamp. */
    public static Message of(MessageRole role, String content) {

        return new Message(null, null, role, content, null);
    }

    public boolean persisted() {
        
        return id != null;
    }
}
