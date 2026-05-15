CREATE TABLE webhook_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        VARCHAR UNIQUE NOT NULL,
    event_type      VARCHAR NOT NULL,
    payment_id      VARCHAR NOT NULL,
    payload         JSONB NOT NULL,
    headers         JSONB,
    signature       VARCHAR NOT NULL,
    status          VARCHAR NOT NULL DEFAULT 'RECEIVED',
    failure_type    VARCHAR,
    failure_reason  TEXT,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    event_created_at TIMESTAMPTZ,
    processed_at    TIMESTAMPTZ,
    retry_count     INT NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    correlation_id  UUID NOT NULL
);