CREATE TABLE payment_records (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id        VARCHAR UNIQUE NOT NULL,
    order_id          VARCHAR,
    internal_order_id UUID,
    amount            BIGINT NOT NULL,
    currency          VARCHAR(3) NOT NULL,
    status            VARCHAR NOT NULL,
    method            VARCHAR,
    email             VARCHAR,
    contact           VARCHAR,
    captured_at       TIMESTAMPTZ,
    retry_count       INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    correlation_id    UUID NOT NULL
);