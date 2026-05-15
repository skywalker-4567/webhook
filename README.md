# Razorpay Webhook Processor & Payment Ledger

A production-grade distributed payment processing system built on top of the Razorpay payment gateway. Handles high-volume webhook ingestion, maintains a double-entry financial ledger, detects fraud in real time, and provides a full-featured React dashboard — all deployed via Docker Compose with two load-balanced application instances.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Key Features](#key-features)
- [Tech Stack](#tech-stack)
- [System Design Decisions](#system-design-decisions)
- [Database Schema](#database-schema)
- [API Reference](#api-reference)
- [Getting Started](#getting-started)
- [Running Tests](#running-tests)
- [Project Structure](#project-structure)
- [Environment Variables](#environment-variables)

---

## Architecture Overview

```
                          ┌─────────────────────────────────────────────┐
                          │                  Nginx :9090                │
                          │         (Reverse Proxy + Load Balancer)     │
                          └──────────────┬──────────────────────────────┘
                                         │
                    ┌────────────────────┴─────────────────────┐
                    │                                          │
          ┌─────────▼──────────┐                   ┌──────────▼─────────┐
          │   Spring Boot      │                   │   Spring Boot      │
          │   App Instance 1   │                   │   App Instance 2   │
          │   :8080            │                   │   :8080            │
          └─────────┬──────────┘                   └──────────┬─────────┘
                    │                                          │
          ┌─────────▼──────────────────────────────────────────▼─────────┐
          │                     PostgreSQL 15                             │
          │   webhook_events · payment_records · ledger_entries           │
          │   audit_log · orders · fraud_checks · users · ...            │
          └─────────────────────────────────────────────────────────────-┘
                    │                                          │
          ┌─────────▼──────────┐                   ┌──────────▼─────────┐
          │     Redis 7        │                   │   FastAPI ML       │
          │  Distributed Locks │                   │   Fraud Scoring    │
          │  Leader Election   │                   │   (IsolationForest)│
          │  Rate Limiting     │                   │   :8000            │
          └────────────────────┘                   └────────────────────┘

          ┌──────────────────────────────────────────────────────────────┐
          │                  React Dashboard :9090/                      │
          │   Login · Dashboard · Payments · Ledger · Audit · Webhooks  │
          └──────────────────────────────────────────────────────────────┘
```

**Request flow for a Razorpay webhook:**

1. Razorpay sends `POST /webhooks/razorpay` with `X-Razorpay-Signature`
2. Nginx load-balances to app-1 or app-2
3. Spring Boot verifies the HMAC-SHA256 signature and saves the event
4. After transaction commits, an async event fires (`afterCommit`)
5. `WebhookProcessingService` acquires a DB lock and transitions status to `PROCESSING`
6. `PaymentService` updates or creates the payment record
7. `LedgerService` writes a balanced DEBIT + CREDIT pair (after commit, `REQUIRES_NEW`)
8. `AuditService` appends a hash-chained entry (single-thread executor, Redis lock)
9. `FraudService` runs rule engine synchronously, then calls ML service asynchronously

---

## Key Features

### Webhook Ingestion
- HMAC-SHA256 signature verification on every request
- Idempotent processing via unique constraint on `event_id`
- Automatic retry with exponential backoff (max 3 retries)
- Full event lifecycle: `RECEIVED → PROCESSING → PROCESSED | FAILED`
- Correlation ID propagated through all downstream services and MDC logs

### Double-Entry Ledger
- Every payment event produces exactly one DEBIT and one CREDIT
- Both entries share a `transaction_id` UUID for linkage
- Ledger is **append-only** — no UPDATE, no DELETE, ever
- Account types: `CUSTOMER`, `MERCHANT`, `GATEWAY`
- Entry types per event:
  - `PAYMENT`: DEBIT CUSTOMER → CREDIT GATEWAY
  - `REFUND`: DEBIT GATEWAY → CREDIT CUSTOMER
  - `SETTLEMENT`: DEBIT GATEWAY → CREDIT MERCHANT
- Balance queries support point-in-time (`asOf`) lookups

### Audit Hash Chain
- Every state change produces an immutable audit log entry
- SHA-256 hash chain: each entry's hash includes the previous entry's hash
- Genesis anchor: `previousHash = "GENESIS"`
- Two-phase write: `saveAndFlush` → read real `BIGSERIAL` → update hash (native query)
- Protected by a single-thread executor + Redis distributed lock to prevent race conditions
- `/audit/verify` endpoint walks the chain and reports the exact sequence where it breaks

### Fraud Detection
- **Rule engine** (synchronous, zero I/O):
  - `HIGH_AMOUNT`: amount > ₹1,000 (100,000 paise)
  - `REPEATED_FAILURE`: status = FAILED and retryCount ≥ 2
  - `RAPID_STATE_CHANGE`: state changed within 5 seconds of creation
- **ML scoring** (asynchronous, guarded):
  - FastAPI service running scikit-learn `IsolationForest`
  - Only called when `amount > 50,000 paise OR retryCount > 0`
  - 2s connect / 3s read timeout — never blocks payment processing
  - Returns `fraud_score` (float) and `is_anomaly` (bool)

### Distributed Infrastructure
- Redis-backed distributed locking with TTL and owner verification
- Leader election per scheduler (reconciliation, ledger retry, webhook retry)
- Token bucket rate limiting per client IP
- Both app instances share the same PostgreSQL and Redis — no split-brain

### Order Management
- Creates Razorpay orders via REST API
- Idempotent via `X-Idempotency-Key` header with race condition guard (`DataIntegrityViolationException`)
- Links incoming webhook payments back to internal orders

### Settlement & Reconciliation
- CSV export of ledger entries for any date range (max 31 days)
- Summary report: total payments, total refunds, net amount
- Nightly reconciliation scheduler compares internal status vs gateway status
- Logs `CORRECTED` or `SKIPPED` actions with reasons

### Security
- JWT Bearer token authentication on all endpoints except webhook ingestion
- Webhook auth is HMAC signature — no JWT needed (Razorpay signs the request)
- BCrypt password hashing
- Role-based access: `OPERATOR` and `ADMIN`
- `POST /auth/register` is ADMIN-only

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.2.5, Java 21 |
| ORM | Hibernate 6.4, Spring Data JPA |
| Database | PostgreSQL 15 |
| Migrations | Flyway (V1–V15) |
| Cache / Locks | Redis 7 |
| Security | Spring Security 6, JJWT |
| Async | Spring `@Async`, `@TransactionalEventListener` |
| ML Service | Python 3.11, FastAPI, scikit-learn |
| Frontend | React 18, TypeScript, Vite |
| Styling | Tailwind CSS |
| Data Fetching | TanStack React Query v5 |
| Infrastructure | Docker, Docker Compose, Nginx |
| Code Generation | Lombok |

---

## System Design Decisions

### Why `afterCommit()` for event publishing?
Publishing Spring application events inside a `@Transactional` method means the downstream listener could read data that hasn't been committed yet. Using `TransactionSynchronizationManager.registerSynchronization().afterCommit()` guarantees the webhook event row is visible to all readers before the async processing begins.

### Why a single-thread executor for audit writes?
The hash chain requires strict sequential ordering. If two audit writes race, the second one might read a stale `previousHash`. A single-thread executor (`Executors.newSingleThreadExecutor`) combined with a Redis distributed lock ensures only one write happens at a time across both app instances.

### Why `BIGSERIAL` without `@GeneratedValue`?
Hibernate's `@GeneratedValue` with `IDENTITY` strategy reads the generated value back after insert, but only if the entity mapping is correct. Using `insertable=false, updatable=false` on the `sequenceNum` field and calling `entityManager.refresh()` after `saveAndFlush()` guarantees we always get the real database-generated sequence number before computing the hash.

### Why `transactionRef = eventId`, not `paymentId`?
A single payment can generate multiple events (captured, refunded). Using `paymentId` as the ledger idempotency key would prevent writing the refund entry. Using `eventId` scopes idempotency to the specific webhook event.

### Why two app instances?
Demonstrates horizontal scalability. Both instances share PostgreSQL and Redis. The distributed lock service ensures only one instance holds the leader role for each scheduled job at a time.

---

## Database Schema

15 Flyway migrations (V1–V15) covering:

| Table | Purpose |
|---|---|
| `webhook_events` | Raw webhook storage with full lifecycle tracking |
| `payment_records` | Deduplicated payment state (AUTHORIZED → CAPTURED → REFUNDED) |
| `orders` | Razorpay orders linked to internal records |
| `ledger_accounts` | Three accounts: CUSTOMER, MERCHANT, GATEWAY |
| `ledger_entries` | Append-only double-entry bookkeeping |
| `audit_log` | Hash-chained immutable audit trail |
| `reconciliation_log` | Gateway vs internal status comparison results |
| `settlement_reports` | Aggregated settlement periods |
| `refund_records` | Idempotent refund tracking |
| `fraud_checks` | Rule engine + ML results per payment |
| `ledger_retry_queue` | Failed ledger writes queued for retry |
| `users` | JWT auth users with BCrypt passwords |
| `api_keys` | SHA-256 hashed API keys for M2M auth |

---

## API Reference

All endpoints except `POST /webhooks/razorpay` and `GET /actuator/health` require `Authorization: Bearer <token>`.

### Authentication
```
POST /auth/login        { username, password } → { token, expiresIn: 3600 }
POST /auth/register     ADMIN only — { username, password, role }
```

### Webhooks
```
POST /webhooks/razorpay         Ingest webhook (HMAC auth, no JWT)
GET  /webhooks/events           Paginated event list with filters
GET  /webhooks/stats            Counts grouped by status
```

### Payments
```
GET  /payments                  Paginated list, filterable by status
GET  /payments/{paymentId}      Single payment detail
POST /payments/{paymentId}/refund   Initiate refund (idempotent)
```

### Orders
```
POST /orders                    Create Razorpay order (idempotent via X-Idempotency-Key)
GET  /orders/{orderId}          Order detail with linked payment
```

### Ledger
```
GET  /ledger?paymentId={}       Entries for a payment
GET  /ledger?accountType={}&from={}&to={}   Account statement
GET  /ledger/accounts/{accountType}/balance?asOf={}   Point-in-time balance
```

### Settlement
```
GET  /settlement/summary?from={}&to={}   Aggregated summary
GET  /settlement/report?from={}&to={}    CSV export (max 31 days)
```

### Audit
```
GET  /audit/logs?entityId={}&entityType={}    Paginated audit trail
GET  /audit/verify?entityId={}&entityType={}  Verify hash chain integrity
```

### Fraud
```
GET  /fraud-checks?paymentId={}   All fraud check results for a payment
```

---

## Getting Started

### Prerequisites
- Docker Desktop
- Git

### 1. Clone the repository
```bash
git clone https://github.com/yourusername/razorpay-webhook.git
cd razorpay-webhook
```

### 2. Configure environment
```bash
cp .env.example .env
```

Edit `.env` and fill in:
```
RAZORPAY_KEY_ID=rzp_test_xxxxxxxxxxxx
RAZORPAY_KEY_SECRET=your_key_secret
RAZORPAY_WEBHOOK_SECRET=your_webhook_secret
JWT_SECRET=<base64-encoded-256-bit-secret>
DB_PASSWORD=your_db_password
```

### 3. Start all services
```bash
docker compose up -d
```

This starts: PostgreSQL, Redis, ML service, two Spring Boot instances, React frontend, Nginx.

### 4. Open the dashboard
Navigate to `http://localhost:9090`

Login with:
- Username: `admin`
- Password: `admin123`

---

## Running Tests

A 36-point PowerShell smoke test covers the full happy path end to end.

```powershell
# Clear data first
docker exec -it razorpay-postgres psql -U postgres -d razorpay_webhook -c "TRUNCATE webhook_events, payment_records, ledger_entries, audit_log, fraud_checks, orders, reconciliation_log, settlement_reports, refund_records, ledger_retry_queue, api_keys RESTART IDENTITY CASCADE;"

# Run tests
.\test\smoke-test.ps1 -BaseUrl "http://localhost:9090" -WebhookSecret "your_webhook_secret"
```

**Test coverage:**
- Health check
- JWT login and bad credentials
- Webhook ingestion, idempotency, signature rejection
- Payment record creation and status
- Fraud detection (HIGH_AMOUNT rule + ML scoring)
- Double-entry ledger balance verification
- Audit hash chain integrity
- Reconciliation endpoint
- Settlement summary and CSV export
- Order creation and idempotency
- JWT protection on all secured endpoints
- Webhook stats

**Result: 36/36 passing**

---

## Project Structure

```
razorpay-webhook/
├── src/main/java/com/example/razorpaywebhook/
│   ├── config/          # Async executors, Redis, Security, CORS
│   ├── controller/      # REST controllers (thin — no business logic)
│   ├── distributed/     # DistributedLockService, LeaderElectionService, RateLimiterService
│   ├── domain/entity/   # JPA entities (15 tables)
│   ├── dto/             # Request/response DTOs
│   ├── enums/           # All enum types
│   ├── event/           # Spring application events (records)
│   ├── exception/       # Domain exceptions + GlobalExceptionHandler
│   ├── repository/      # Spring Data JPA repositories
│   ├── security/        # JWT, AuthService, SecurityConfig
│   └── service/         # All business logic
│       ├── WebhookIngestionService.java
│       ├── WebhookProcessingService.java
│       ├── PaymentService.java
│       ├── LedgerService.java
│       ├── AuditService.java
│       ├── FraudService.java
│       ├── FraudRuleEngine.java
│       ├── MLClient.java
│       ├── OrderService.java
│       ├── RefundService.java
│       ├── SettlementService.java
│       └── ReconciliationScheduler.java
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/    # V1–V15 Flyway migrations
├── frontend/
│   ├── src/
│   │   ├── components/  # Reusable UI components
│   │   ├── hooks/       # React Query hooks per domain
│   │   ├── pages/       # Dashboard, Payments, Ledger, Audit, Login
│   │   ├── services/    # API service layer
│   │   └── types/       # TypeScript interfaces
│   └── Dockerfile
├── ml-service/
│   ├── main.py          # FastAPI app with IsolationForest scoring
│   └── requirements.txt
├── nginx/
│   └── nginx.conf       # Upstream load balancer config
├── test/
│   └── smoke-test.ps1   # 36-point end-to-end test suite
├── docker-compose.yml
└── .env.example
```

---

## Environment Variables

| Variable | Description | Default |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/razorpay_webhook` |
| `DB_USER` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | — |
| `REDIS_HOST` | Redis hostname | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `RAZORPAY_KEY_ID` | Razorpay API key ID | — |
| `RAZORPAY_KEY_SECRET` | Razorpay API key secret | — |
| `RAZORPAY_WEBHOOK_SECRET` | Webhook HMAC secret (set in Razorpay dashboard) | — |
| `JWT_SECRET` | Base64-encoded 256-bit JWT signing key | — |
| `JWT_EXPIRY` | JWT expiry in seconds | `3600` |
| `ML_SERVICE_URL` | Internal ML service URL | `http://ml-service:8000` |
| `SERVER_PORT` | Application port | `8080` |

---

## License

MIT
