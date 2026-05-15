CREATE TABLE ledger_retry_queue (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type          VARCHAR NOT NULL,
    event_payload       JSONB NOT NULL,
    retry_count         INT NOT NULL DEFAULT 0,
    next_retry_at       TIMESTAMPTZ,
    last_failure_reason TEXT,
    status              VARCHAR NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);