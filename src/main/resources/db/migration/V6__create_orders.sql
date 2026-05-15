CREATE TABLE orders (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key   VARCHAR UNIQUE NOT NULL,
    razorpay_order_id VARCHAR UNIQUE NOT NULL,
    customer_id       VARCHAR NOT NULL,
    amount            BIGINT NOT NULL,
    currency          VARCHAR(3) NOT NULL,
    status            VARCHAR NOT NULL DEFAULT 'CREATED',
    description       TEXT,
    metadata          JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE payment_records
    ADD CONSTRAINT fk_payment_order
    FOREIGN KEY (internal_order_id) REFERENCES orders(id);