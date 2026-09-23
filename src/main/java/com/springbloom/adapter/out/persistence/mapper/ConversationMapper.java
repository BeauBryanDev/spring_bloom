package com.springbloom.adapter.out.persistence.mapper;

import java.util.List;

import com.springbloom.adapter.out.persistence.entity.ConversationEntity;
import com.springbloom.adapter.out.persistence.entity.MessageEntity;
import com.springbloom.domain.model.Conversation;
import com.springbloom.domain.model.Message;

/**
 * Entity to domain, one direction only. Conversations are created and appended
 * to through explicit repository calls rather than by saving a whole graph, so
 * there is no toEntity for the aggregate.
 */
public class ConversationMapper {

    private ConversationMapper() {
    }

    /** Without messages: the tail is loaded separately and bounded. */
    public static Conversation toDomain(ConversationEntity entity) {
        return toDomain(entity, List.of());
    }

    public static Conversation toDomain(ConversationEntity entity, List<Message> messages) {
        return new Conversation(
                entity.getId(),
                entity.getCustomerId(),
                entity.getSessionKey(),
                messages,
                entity.getCreatedAt());
    }

    /**
     * getConversation().getId() is safe on a lazy proxy: the identifier comes
     * from the FK already in hand, so it does not trigger a select. Touching any
     * other property of that proxy here would.
     */
    public static Message toDomain(MessageEntity entity) {
        return new Message(
                entity.getId(),
                entity.getConversation().getId(),
                entity.getRole(),
                entity.getContent(),
                entity.getCreatedAt());
    }
}
