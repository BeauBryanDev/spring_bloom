-- Token usage of every agent turn, for the admin cost dashboard.
-- The cache columns exist now so prompt caching needs no further migration.
CREATE TABLE agent_usage (
    id BIGSERIAL PRIMARY KEY,
    conversation_id UUID REFERENCES conversation (id) ON DELETE SET NULL,
    model VARCHAR(80) NOT NULL,
    input_tokens INTEGER NOT NULL,
    output_tokens INTEGER NOT NULL,
    cache_read_tokens INTEGER NOT NULL DEFAULT 0,
    cache_write_tokens INTEGER NOT NULL DEFAULT 0,
    had_image BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_agent_usage_non_negative CHECK (
        input_tokens >= 0 AND output_tokens >= 0
        AND cache_read_tokens >= 0 AND cache_write_tokens >= 0)
);

CREATE INDEX ix_agent_usage_created_at ON agent_usage (created_at);
