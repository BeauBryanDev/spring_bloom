package com.springbloom.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.springbloom.domain.model.Conversation;
import com.springbloom.domain.model.Message;
import com.springbloom.domain.model.MessageRole;

/**
 * Stores anonymous chat sessions and their turns.
 *
 * Unlike a quotation, a conversation is not saved as one aggregate: a chat
 * appends one message per turn, and rewriting the whole history to add a line
 * would grow more expensive with every turn. Messages are therefore appended
 * individually and read back as a bounded tail.
 */
public interface ConversationRepository {

    /**
     * The entry point for every chat request: returns the session's conversation,
     * starting one if this session key has not been seen. Concurrent first
     * requests for the same key must resolve to the same conversation rather
     * than colliding on the UNIQUE constraint.
     *
     * The returned conversation carries no messages; ask for history separately.
     */
    Conversation findOrStart(String sessionKey);

    Optional<Conversation> findBySessionKey(String sessionKey);

    Optional<Conversation> findById(UUID conversationId);

    /** Appends one turn and returns it with its assigned id and timestamp. */
    Message append(UUID conversationId, MessageRole role, String content);

    /**
     * The most recent turns, oldest first so the result can be replayed straight
     * into a prompt. Bounded because a context window is: an unbounded history
     * would eventually cost more than the answer.
     */
    List<Message> history(UUID conversationId, int limit);

    /** Attaches a customer once the chat has produced one. */
    Conversation assignCustomer(UUID conversationId, Long customerId);
}
