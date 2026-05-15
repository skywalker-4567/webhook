CREATE TABLE ledger_accounts (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_type VARCHAR NOT NULL,
    account_code VARCHAR UNIQUE NOT NULL,
    description  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO ledger_accounts (account_type, account_code, description) VALUES
    ('CUSTOMER', 'ACC_CUSTOMER', 'Customer liability account'),
    ('MERCHANT', 'ACC_MERCHANT', 'Merchant payable account'),
    ('GATEWAY',  'ACC_GATEWAY',  'Payment gateway account');