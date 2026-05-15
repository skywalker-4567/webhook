-- ═══════════════════════════════════════════════════════════════════════════
-- Razorpay Webhook Processor — SQL Verification Queries
-- Run against: razorpay_webhook database
-- Usage: psql -U postgres -d razorpay_webhook -f test/verify.sql
-- ═══════════════════════════════════════════════════════════════════════════

-- ─── 1. Verify all tables exist ─────────────────────────────────────────────
\echo '--- All tables in public schema ---'
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
ORDER BY table_name;

-- ─── 2. Webhook event counts by status ──────────────────────────────────────
\echo '--- Webhook event counts by status ---'
SELECT status, COUNT(*) AS count
FROM webhook_events
GROUP BY status
ORDER BY status;

-- ─── 3. Payment record counts by status ─────────────────────────────────────
\echo '--- Payment record counts by status ---'
SELECT status, COUNT(*) AS count
FROM payment_records
GROUP BY status
ORDER BY status;

-- ─── 4. Verify ledger double-entry for a specific payment ───────────────────
-- Replace :paymentId with an actual payment ID from your test run
\echo '--- Ledger entries for latest payment ---'
SELECT
    le.transaction_id,
    le.transaction_ref,
    le.entry_type,
    le.amount,
    la.account_code,
    la.account_type,
    le.created_at
FROM ledger_entries le
JOIN ledger_accounts la ON la.id = le.account_id
WHERE le.payment_id = (
    SELECT payment_id FROM payment_records ORDER BY created_at DESC LIMIT 1
)
ORDER BY le.entry_type;

-- ─── 5. Assert double-entry balance per transaction ─────────────────────────
\echo '--- Double-entry balance check (should show 0 for all) ---'
SELECT
    transaction_id,
    SUM(CASE WHEN entry_type = 'DEBIT'  THEN amount ELSE 0 END) AS total_debit,
    SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END) AS total_credit,
    SUM(CASE WHEN entry_type = 'DEBIT'  THEN amount ELSE 0 END) -
    SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END) AS imbalance
FROM ledger_entries
GROUP BY transaction_id
HAVING
    SUM(CASE WHEN entry_type = 'DEBIT'  THEN amount ELSE 0 END) !=
    SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END);

-- (empty result = all balanced ✓)

-- ─── 6. Verify transactionRef = eventId (not paymentId) ─────────────────────
\echo '--- transactionRef vs eventId check ---'
SELECT
    le.transaction_ref,
    we.event_id,
    le.payment_id,
    CASE
        WHEN le.transaction_ref = we.event_id   THEN 'CORRECT (ref=eventId)'
        WHEN le.transaction_ref = le.payment_id THEN 'BUG: ref=paymentId'
        ELSE 'UNKNOWN'
    END AS ref_check
FROM ledger_entries le
JOIN webhook_events we ON we.payment_id = le.payment_id
WHERE le.transaction_type = 'PAYMENT'
LIMIT 5;

-- ─── 7. Audit chain integrity ────────────────────────────────────────────────
\echo '--- Audit log chain (last 20 entries) ---'
SELECT
    sequence_num,
    entity_type,
    entity_id,
    action,
    actor,
    LEFT(previous_hash, 16) || '...' AS prev_hash_short,
    LEFT(current_hash,  16) || '...' AS curr_hash_short,
    created_at
FROM audit_log
ORDER BY sequence_num ASC
LIMIT 20;

-- ─── 8. PENDING hashes (two-phase write failures) ───────────────────────────
\echo '--- Audit entries with PENDING hash (should be 0) ---'
SELECT COUNT(*) AS pending_hashes
FROM audit_log
WHERE current_hash = 'PENDING';

-- ─── 9. Fraud checks (latest 10) ────────────────────────────────────────────
\echo '--- Latest fraud checks ---'
SELECT
    payment_id,
    event_type,
    payment_status,
    is_fraud,
    triggered_rules,
    ml_fraud_score,
    ml_is_anomaly,
    created_at
FROM fraud_checks
ORDER BY created_at DESC
LIMIT 10;

-- ─── 10. Correlation ID propagation check ───────────────────────────────────
\echo '--- Correlation ID propagation (webhook → payment) ---'
SELECT
    we.correlation_id,
    we.event_id,
    we.event_type,
    pr.payment_id,
    pr.status AS payment_status,
    CASE
        WHEN we.correlation_id = pr.correlation_id THEN '✓ MATCH'
        ELSE '✗ MISMATCH'
    END AS correlation_check
FROM webhook_events we
JOIN payment_records pr ON pr.payment_id = we.payment_id
WHERE we.correlation_id IS NOT NULL
LIMIT 5;

-- ─── 11. Ledger account balances ────────────────────────────────────────────
\echo '--- Ledger account balances ---'
SELECT
    la.account_code,
    la.account_type,
    SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END) -
    SUM(CASE WHEN le.entry_type = 'DEBIT'  THEN le.amount ELSE 0 END) AS balance,
    COUNT(*) AS entry_count
FROM ledger_accounts la
LEFT JOIN ledger_entries le ON le.account_id = la.id
GROUP BY la.id, la.account_code, la.account_type
ORDER BY la.account_type;

-- ─── 12. Stuck webhooks (PROCESSING > 10 minutes) ───────────────────────────
\echo '--- Stuck webhooks (PROCESSING > 10 min) ---'
SELECT event_id, event_type, payment_id, status, updated_at
FROM webhook_events
WHERE status = 'PROCESSING'
  AND updated_at < NOW() - INTERVAL '10 minutes';

-- ─── 13. Ledger retry queue ──────────────────────────────────────────────────
\echo '--- Ledger retry queue ---'
SELECT
    event_type,
    retry_count,
    status,
    last_failure_reason,
    next_retry_at,
    created_at
FROM ledger_retry_queue
ORDER BY created_at DESC
LIMIT 10;

-- ─── 14. Recent orders ──────────────────────────────────────────────────────
\echo '--- Recent orders ---'
SELECT
    id,
    idempotency_key,
    razorpay_order_id,
    customer_id,
    amount,
    currency,
    status,
    created_at
FROM orders
ORDER BY created_at DESC
LIMIT 10;