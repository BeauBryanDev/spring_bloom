package com.springbloom.domain.service;

/** The agent could not answer: the model or its provider failed, not the customer. */
public class AgentUnavailableException extends RuntimeException {

    public AgentUnavailableException(Throwable cause) {
        super("The agent is unavailable", cause);
    }
}
