package com.springbloom.domain.model;

/** Mirrors the message_role Postgres enum. The constants match the DB values exactly. */
public enum MessageRole {

    USER,
    ASSISTANT,
    SYSTEM;

    /** SYSTEM messages are prompt scaffolding, never shown back to the customer. */
    public boolean visibleToCustomer() {
        return this != SYSTEM;
    }
}
