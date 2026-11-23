package com.springbloom.domain.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.springbloom.domain.model.GuardrailEvent;
import com.springbloom.domain.model.GuardrailEvent.Stage;

public interface GuardrailEventRepository {

    void record(UUID conversationId, Stage stage, String rule);

    /** Counts per stage and rule since the instant; null means all time. */
    List<GuardrailEvent> summarySince(Instant from);
}
