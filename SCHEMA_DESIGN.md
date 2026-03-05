# PayRoute Database Schema Design Document

## Overview

This document explains the database schema design for PayRoute's cross-border payment processing system. The schema is optimized for **financial correctness**, **audit trail completeness**, and **operational resilience**.

---

## Design Principles

1. **Double-entry bookkeeping**: Every financial transaction creates balanced ledger entries (DEBIT = CREDIT)
2. **Immutability**: Financial records are append-only; updates create new rows rather than modifying existing ones
3. **Idempotency**: All operations can be safely retried without creating duplicate side effects
4. **State machine enforcement**: Invalid state transitions are prevented at the application layer

---

## Core Schema

### 1. Accounts & Balances

```sql
CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE account_balances (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    currency TEXT NOT NULL,
    available NUMERIC(18,2) NOT NULL CHECK (available >= 0),
    locked NUMERIC(18,2) NOT NULL CHECK (locked >= 0),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (account_id, currency)
);
```

**Why separate `available` and `locked`?**

When a payment is initiated, funds move from `available` → `locked` to prevent double-spending. On completion, `locked` → 0 (funds leave the system). On failure, `locked` → `available` (reversal).

Alternative considered: Single `balance` column with a separate `holds` table tracking locked amounts. Rejected because:
- Requires join to compute available balance (performance penalty)
- More complex to ensure consistency (two tables to update atomically)

**Why `NUMERIC(18,2)` instead of `INTEGER` (cents)?**

Crypto payments and some currencies (e.g., JPY) don't use decimal places. Using `NUMERIC` provides flexibility for future expansion while maintaining precision for fiat currencies.

---

### 2. FX Quotes

```sql
CREATE TABLE fx_quotes (
    id UUID PRIMARY KEY,
    base_currency TEXT NOT NULL,
    quote_currency TEXT NOT NULL,
    rate NUMERIC(18,8) NOT NULL CHECK (rate > 0),
    status TEXT NOT NULL DEFAULT 'LOCKED',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    locked_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_fx_pair ON fx_quotes(base_currency, quote_currency);
```

**Why store quotes instead of calculating on-the-fly?**

Auditability: If a payment dispute arises, we can prove what rate was applied at the time of initiation. Regulatory compliance (MiFID II, Dodd-Frank) requires storing pre-trade quotes.

**Why `NUMERIC(18,8)` for rate?**

Some currency pairs (e.g., JPY/KRW) have extreme ratios requiring high precision. 8 decimal places accommodates cryptocurrencies (e.g., 1 BTC = 0.00001 satoshi).

---

### 3. Transactions

```sql
CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    reference TEXT NOT NULL UNIQUE,
    sender_account_id UUID NOT NULL REFERENCES accounts(id),
    recipient_account_id UUID NOT NULL REFERENCES accounts(id),
    source_currency TEXT NOT NULL,
    destination_currency TEXT NOT NULL,
    source_amount NUMERIC(18,2) NOT NULL CHECK (source_amount > 0),
    destination_amount NUMERIC(18,2) NOT NULL CHECK (destination_amount > 0),
    status TEXT NOT NULL CHECK (status IN ('INITIATED','PROCESSING','COMPLETED','FAILED')),
    provider_reference TEXT UNIQUE,
    fx_quote_id UUID NOT NULL REFERENCES fx_quotes(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_tx_status_created ON transactions(status, created_at);
```

**Why denormalize `source_amount` and `destination_amount`?**

Could derive destination amount from `source_amount * fx_quote.rate`, but:
- FX provider might return a slightly different amount due to rounding/fees
- Denormalization ensures the **exact** amount credited matches what was quoted
- Avoids floating-point precision bugs (e.g., 100 * 0.92 = 91.9999998)

**Why `provider_reference` is nullable initially?**

The provider returns this reference **after** we submit the payment. Workflow:
1. Create transaction with `status=INITIATED`
2. Call provider API
3. Update with `provider_reference` and `status=PROCESSING`

Alternative considered: Two-phase insert (insert after getting provider reference). Rejected because if the provider call times out, we lose the transaction record entirely.

---

### 4. Status History

```sql
CREATE TABLE transaction_status_history (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    from_status TEXT NOT NULL,
    to_status TEXT NOT NULL,
    reason TEXT,
    at_time TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_hist_tx ON transaction_status_history(transaction_id, at_time);
```

**Why separate history table instead of updating `transactions.status`?**

- Audit trail: Regulators require full lifecycle visibility
- Debugging: Can trace exactly when/why a payment failed
- Analytics: Can measure time spent in each state (e.g., average time in `PROCESSING`)

Immutability: We still update `transactions.status` for convenience (queries like "show all failed payments"), but the history table is append-only.

---

### 5. Ledger

```sql
CREATE TABLE ledger_accounts (
    id UUID PRIMARY KEY,
    code TEXT NOT NULL,
    currency TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('ASSET','LIABILITY','REVENUE','EXPENSE')),
    UNIQUE(code, currency)
);

CREATE TABLE ledger_postings (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    posting_type TEXT NOT NULL, -- DEBIT_LOCK, SETTLE, REVERSE
    unique_key TEXT NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    posting_id UUID NOT NULL REFERENCES ledger_postings(id),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    ledger_account_id UUID NOT NULL REFERENCES ledger_accounts(id),
    currency TEXT NOT NULL,
    amount NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    direction TEXT NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

**Why three tables instead of one `ledger` table?**

- `ledger_accounts`: Chart of accounts (e.g., `CUST_AVAILABLE_USD`, `PROVIDER_CLEARING_EUR`)
- `ledger_postings`: Groups entries into atomic operations (e.g., "lock funds" creates 2 entries: debit available, credit locked)
- `ledger_entries`: Individual debit/credit lines

This structure ensures:
- **Atomicity**: All entries in a posting succeed or fail together
- **Idempotency**: `ledger_postings.unique_key` prevents duplicate postings
- **Immutability**: Entries are never updated (reversals create new entries)

**Why `unique_key` instead of relying on transaction ID?**

A single transaction creates multiple postings:
1. Lock: `CUST_AVAILABLE → CUST_LOCKED`
2. Settle: `CUST_LOCKED → PROVIDER_CLEARING` + `PROVIDER_CLEARING → RECIPIENT_AVAILABLE`

Each posting needs a unique key (e.g., `"lock:{txId}"`, `"settle-src:{txId}"`, `"settle-dest:{txId}"`).

---

### 6. Webhook Events

```sql
CREATE TABLE webhook_events (
    id UUID PRIMARY KEY,
    provider_reference TEXT,
    event_fingerprint TEXT NOT NULL,
    raw_body TEXT NOT NULL,
    signature TEXT NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT now(),
    processed_at TIMESTAMP,
    processing_status TEXT NOT NULL CHECK (processing_status IN ('RECEIVED','PROCESSED','DUPLICATE','ERROR')),
    error_message TEXT,
    UNIQUE (provider_reference, event_fingerprint)
);
CREATE INDEX idx_webhook_provider_ref ON webhook_events(provider_reference);
```

**Why store raw webhook body?**

- Forensics: Can replay webhooks if processing logic had a bug
- Compliance: Some regulations require storing all received messages
- Debugging: Can inspect exact payload provider sent (vs. our parsed representation)

**How does `UNIQUE (provider_reference, event_fingerprint)` ensure idempotency?**

`event_fingerprint = SHA256(raw_body)`. If provider sends identical webhook twice:
- First attempt: Inserts successfully
- Second attempt: Violates unique constraint → returns immediately

Note: We still return `200 OK` to prevent provider from retrying infinitely.

---

### 7. Idempotency

```sql
CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    endpoint TEXT NOT NULL,
    idem_key TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    response_json TEXT NOT NULL,
    transaction_id UUID REFERENCES transactions(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (endpoint, idem_key)
);
```

**Why store `request_hash`?**

Prevents attack where client sends:
- Request A with `Idempotency-Key: abc` → creates payment for $100
- Request B with `Idempotency-Key: abc` → different payload (e.g., $10,000)

Without hash check, Request B would return Response A (cached), but the client might interpret it as "I successfully created a $10k payment."

With hash check:
```java
if (existingRecord.requestHash != SHA256(newRequest)) {
    throw new ConflictException("Idempotency key reused with different request");
}
```

---

## Ensuring Balance Integrity

### Invariant: No Money is Created or Destroyed

Every ledger posting creates **exactly two entries** (one DEBIT, one CREDIT) with **equal amounts**:

```java
LedgerEntry debit = new LedgerEntry(DEBIT, amount);
LedgerEntry credit = new LedgerEntry(CREDIT, amount);
entryRepo.saveAll(List.of(debit, credit));
```

**Verification query:**
```sql
-- Sum of all ledger entries must equal zero
SELECT SUM(CASE direction WHEN 'DEBIT' THEN amount ELSE -amount END) AS net
FROM ledger_entries;
-- Expected: 0.00
```

**Account balance reconciliation:**
```sql
-- Actual balance (denormalized)
SELECT account_id, currency, available + locked AS total
FROM account_balances;

-- Expected balance (from ledger)
SELECT la.code, SUM(CASE le.direction WHEN 'CREDIT' THEN le.amount ELSE -le.amount END) AS balance
FROM ledger_entries le
JOIN ledger_accounts la ON le.ledger_account_id = la.id
WHERE la.code LIKE 'CUST_%'
GROUP BY la.code;
```

If these don't match → bug in ledger or balance update logic.

---

## Adding a New Currency Pair

### Current: NGN → USD, EUR, GBP
### New Requirement: Add NGN → INR

**Steps:**

1. **Add ledger accounts:**
```sql
INSERT INTO ledger_accounts(code, currency, type) VALUES
    ('CUST_AVAILABLE', 'INR', 'LIABILITY'),
    ('CUST_LOCKED', 'INR', 'LIABILITY'),
    ('PROVIDER_CLEARING', 'INR', 'ASSET');
```

2. **Update FX service:**
```java
// FxService.java:26
case "INR" -> new BigDecimal("0.00782"); // 1 NGN = 0.00782 INR
```

3. **No schema changes required** (currency is a TEXT column, not an enum)

**Why TEXT instead of ENUM for currency?**

- Flexibility: Adding EUR → GBP, USD → JPY requires no migration
- Future-proofing: Can add crypto (BTC, ETH) without schema changes

Trade-off: No database-level validation. Mitigated by application-level validation + foreign key to `ledger_accounts`.

---

## What I Would Do Differently with More Time

### 1. **Event Sourcing for Transactions**

Instead of updating `transactions.status`, store immutable events:

```sql
CREATE TABLE transaction_events (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL,
    event_type TEXT NOT NULL, -- INITIATED, SUBMITTED, COMPLETED, FAILED
    payload JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

Current state is derived by replaying events:
```sql
SELECT event_type FROM transaction_events
WHERE transaction_id = '...'
ORDER BY created_at DESC LIMIT 1;
```

**Benefits:**
- Complete audit trail (no updates, only inserts)
- Can reconstruct state at any point in time
- Easier to add new event types without migrations

**Trade-off:** More complex queries (need to aggregate events to get current state).

---

### 2. **Partitioning for Scalability**

Partition `transactions` by `created_at` (monthly):

```sql
CREATE TABLE transactions_2025_01 PARTITION OF transactions
FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
```

**Benefits:**
- Archival: Drop old partitions instead of slow DELETEs
- Query performance: Scans only relevant partition

**Trade-off:** Requires PostgreSQL 10+ and partition management overhead.

---

### 3. **Soft Deletes Instead of Hard Deletes**

Add `deleted_at` to all tables:

```sql
ALTER TABLE transactions ADD COLUMN deleted_at TIMESTAMP;
CREATE INDEX idx_tx_not_deleted ON transactions(id) WHERE deleted_at IS NULL;
```

**Benefits:**
- Accidental deletes are recoverable
- Compliance: Some regulations forbid deleting financial records

**Trade-off:** Queries must filter `WHERE deleted_at IS NULL` (mitigated by partial index).

---

### 4. **JSON Columns for Flexible Metadata**

Add `metadata JSONB` to transactions:

```sql
ALTER TABLE transactions ADD COLUMN metadata JSONB;
```

Store provider-specific fields:
```json
{
  "provider": "stripe",
  "provider_fee": 2.50,
  "settlement_date": "2025-01-20",
  "risk_score": 0.12
}
```

**Benefits:**
- No schema migration to add provider-specific fields
- Queryable via GIN indexes: `CREATE INDEX idx_metadata ON transactions USING GIN(metadata);`

**Trade-off:** Lacks schema validation (can store garbage). Mitigate with application-level validation or `CHECK (metadata IS NULL OR jsonb_typeof(metadata) = 'object')`.

---

## Conclusion

This schema prioritizes **correctness** and **auditability** over performance. Trade-offs made:

- **Denormalization** (e.g., `source_amount` + `destination_amount`) for data integrity
- **Append-only tables** (e.g., `ledger_entries`, `webhook_events`) for immutability
- **Unique constraints** (e.g., `idempotency_records`) for idempotency
- **Pessimistic locking** (e.g., `account_balances FOR UPDATE`) for consistency

For a production system handling millions of transactions per day, I would add:
- Read replicas for reporting queries
- CQRS (separate read/write models)
- Caching layer (Redis) for account balances
- Asynchronous processing (Kafka) for webhooks

But for correctness and regulatory compliance, this schema is solid.
