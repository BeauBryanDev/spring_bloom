package com.springbloom.adapter.out.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** conversation_id is a plain UUID: usage is a ledger, not a navigation path into the chat. */
@Entity
@Table(name = "agent_usage")
@Getter
@Setter
@NoArgsConstructor
public class AgentUsageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "model", nullable = false, length = 80)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "cache_read_tokens", nullable = false)
    private int cacheReadTokens;

    @Column(name = "cache_write_tokens", nullable = false)
    private int cacheWriteTokens;

    @Column(name = "had_image", nullable = false)
    private boolean hadImage;

    /** The database default stamps it. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
