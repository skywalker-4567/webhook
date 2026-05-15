CREATE TABLE refund_records (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id         VARCHAR NOT NULL,
    idempotency_key    VARCHAR UNIQUE NOT NULL,
    razorpay_refund_id VARCHAR UNIQUE,
    amount             BIGINT NOT NULL,
    reason             TEXT,
    status             VARCHAR NOT NULL DEFAULT 'INITIATED',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);