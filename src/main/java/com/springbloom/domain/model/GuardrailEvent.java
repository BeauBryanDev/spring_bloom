package com.springbloom.domain.model;

import java.time.Instant;

/** A guardrail firing: where, which rule, and when. Never the text itself. */
public record GuardrailEvent(Stage stage, String rule, long count, Instant lastAt) {

    public enum Stage { INPUT, OUTPUT, TOOL }
}
