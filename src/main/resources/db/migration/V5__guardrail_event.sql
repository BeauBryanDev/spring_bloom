-- Audit of every guardrail firing. Stores the rule, never the customer's text.
CREATE TABLE guardrail_event (
    id BIGSERIAL PRIMARY KEY,
    conversation_id UUID REFERENCES conversation (id) ON DELETE SET NULL,
    stage VARCHAR(10) NOT NULL,
    rule VARCHAR(60) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_guardrail_event_stage CHECK (stage IN ('INPUT', 'OUTPUT', 'TOOL'))
);

CREATE INDEX ix_guardrail_event_created_at ON guardrail_event (created_at);
