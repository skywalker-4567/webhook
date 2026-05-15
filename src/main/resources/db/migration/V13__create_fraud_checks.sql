CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE fraud_checks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id      VARCHAR NOT NULL,
    event_type      VARCHAR NOT NULL,
    payment_status  VARCHAR NOT NULL,
    is_fraud        BOOLEAN NOT NULL,
    triggered_rules JSONB NOT NULL,
    ml_fraud_score  DOUBLE PRECISION,
    ml_is_anomaly   BOOLEAN,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ON fraud_checks(payment_id);