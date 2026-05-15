CREATE TABLE reconciliation_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id      VARCHAR NOT NULL,
    internal_status VARCHAR NOT NULL,
    gateway_status  VARCHAR NOT NULL,
    action_taken    VARCHAR NOT NULL,
    skip_reason     VARCHAR,
    source          VARCHAR,
    reason          TEXT,
    reconciled_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);