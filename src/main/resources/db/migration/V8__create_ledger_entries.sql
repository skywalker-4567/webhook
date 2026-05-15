CREATE TABLE ledger_entries (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_ref  VARCHAR NOT NULL,
    transaction_type VARCHAR NOT NULL,
    entry_type       VARCHAR NOT NULL,
    account_id       UUID NOT NULL REFERENCES ledger_accounts(id),
    amount           BIGINT NOT NULL,
    currency         VARCHAR(3) NOT NULL,
    description      TEXT,
    payment_id       VARCHAR,
    order_id         UUID REFERENCES orders(id),
    transaction_id   UUID NOT NULL,
    correlation_id   UUID,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX ON ledger_entries(transaction_ref, entry_type);