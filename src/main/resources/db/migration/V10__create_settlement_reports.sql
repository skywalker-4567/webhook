CREATE TABLE settlement_reports (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start   TIMESTAMPTZ NOT NULL,
    period_end     TIMESTAMPTZ NOT NULL,
    total_payments BIGINT NOT NULL DEFAULT 0,
    total_refunds  BIGINT NOT NULL DEFAULT 0,
    net_amount     BIGINT NOT NULL DEFAULT 0,
    entry_count    INT NOT NULL DEFAULT 0,
    status         VARCHAR NOT NULL DEFAULT 'GENERATED',
    generated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reconciled_at  TIMESTAMPTZ
);