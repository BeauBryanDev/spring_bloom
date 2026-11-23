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

/** stage is a plain string with a check constraint, not a Postgres enum. */
@Entity
@Table(name = "guardrail_event")
@Getter
@Setter
@NoArgsConstructor
public class GuardrailEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "stage", nullable = false, length = 10)
    private String stage;

    @Column(name = "rule", nullable = false, length = 60)
    private String rule;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
