package com.springbloom.adapter.out.persistence.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.springframework.data.domain.Persistable;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The id is a UUID the application assigns: conversation.id has no database
 * default, so nothing fills it in if we do not. That breaks Spring Data's
 * is-this-new test, which asks whether the id is null - with an id already set,
 * save() takes the merge path and fires a pointless SELECT before every insert,
 * and on a detached instance would silently do nothing. Persistable answers the
 * question explicitly instead.
 */
@Entity
@Table(name = "conversation")
@Getter
@Setter
@NoArgsConstructor
public class ConversationEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id")
    private UUID id;

    /** Null while the chat is anonymous, which is most of its life. */
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "session_key", nullable = false, length = 150, unique = true)
    private String sessionKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Present so a delete cascades and for the rare whole-history read. The chat
     * path appends through MessageJpaRepository instead, which is why this is
     * lazy and batched rather than fetched with the conversation.
     */
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 64)
    private List<MessageEntity> messages = new ArrayList<>();

    @Transient
    private boolean newEntity;

    /** The only way to build one for insertion: it is what marks the row as new. */
    public static ConversationEntity starting(String sessionKey) {
        ConversationEntity entity = new ConversationEntity();
        entity.id = UUID.randomUUID();
        entity.sessionKey = sessionKey;
        entity.newEntity = true;
        return entity;
    }

    /** Sets both sides: a message with a null conversation would insert a null FK. */
    public void addMessage(MessageEntity message) {
        messages.add(message);
        message.setConversation(this);
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostPersist
    @PostLoad
    void settle() {
        newEntity = false;
    }

    @PrePersist
    void onInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
