# PayRoute Take-Home: Technical Analysis

## 4A: Code Review

### Webhook Handler Review

Below is the code under review:

```javascript
app.post('/webhooks/payment-provider', async (req, res) => {
  const payload = req.body;
  const signature = req.headers['x-webhook-signature'];

  if (!signature) {
    return res.status(401).send('Missing signature');
  }

  const transaction = await db.query(
    'SELECT * FROM transactions WHERE provider_reference = $1',
    [payload.reference]
  );

  if (!transaction.rows[0]) {
    return res.status(404).send('Transaction not found');
  }

  if (payload.status === 'completed') {
    await db.query(
      'UPDATE transactions SET status = $1, completed_at = NOW() WHERE id = $2',
      ['completed', transaction.rows[0].id]
    );
    await db.query(
      'UPDATE accounts SET balance = balance + $1 WHERE id = $2',
      [payload.amount, transaction.rows[0].recipient_account_id]
    );
  } else if (payload.status === 'failed') {
    await db.query(
      'UPDATE transactions SET status = $1 WHERE id = $2',
      ['failed', transaction.rows[0].id]
    );
    await db.query(
      'UPDATE accounts SET balance = balance + $1 WHERE id = $2',
      [transaction.rows[0].amount, transaction.rows[0].sender_account_id]
    );
  }

  res.status(200).send('OK');
});
```

---

### Issues Identified

#### **1. Signature Verification Missing**
**Problem:** The code checks if the signature exists but **never validates** it against the webhook body using HMAC.

**Why it matters in payments:** An attacker can send forged webhooks with arbitrary status updates (e.g., marking failed payments as completed). This allows credential  them to steal funds by crediting recipient accounts without the provider actually completing the payment.

**Fix:**
```javascript
const expectedSig = crypto.createHmac('sha256', process.env.WEBHOOK_SECRET)
  .update(JSON.stringify(req.body))
  .digest('hex');

if (!crypto.timingSafeEqual(Buffer.from(expectedSig), Buffer.from(signature))) {
  // Log for forensics but still return 200 to avoid provider retry storms
  await logWebhookEvent(payload, signature, 'INVALID_SIGNATURE');
  return res.status(200).send('OK');
}
```

#### **2. No Raw Body Preservation**
**Problem:** Signature verification requires the **exact raw bytes** of the webhook body. If the body has been parsed (`req.body`), re-stringifying it may not match the original bytes (whitespace, key order).

**Why it matters in payments:** Signature verification will fail intermittently or always, causing legitimate webhooks to be rejected. Providers will retry, creating duplicate processing risks.

**Fix:** Use middleware to cache raw body before JSON parsing:
```javascript
app.use('/webhooks', express.raw({type: 'application/json'}));
// Then parse manually after verification
```

#### **3. Not Idempotent**
**Problem:** If the same webhook is delivered twice (provider retries), the balance updates execute twice, causing **double-crediting** or **double-refunding**.

**Why it matters in payments:** Money is created out of thin air. A $10,000 payment could credit $20,000 if the webhook is replayed.

**Fix:** Log webhook with unique fingerprint (hash of body + provider_reference) before processing:
```javascript
await db.query(
  'INSERT INTO webhook_events (provider_ref, fingerprint, raw_body, signature) VALUES ($1, $2, $3, $4) ON CONFLICT DO NOTHING RETURNING id',
  [payload.reference, hash(rawBody), rawBody, signature]
);
// If no rows returned, webhook already processed
```

#### **4. No Transaction Wrapping**
**Problem:** The transaction status update and balance update are **two separate queries**. If the second fails, the transaction status is updated but the balance is not.

**Why it matters in payments:** The system's internal state becomes inconsistent. A transaction shows as "completed" but the recipient never received funds. Reconciliation becomes impossible without manual intervention.

**Fix:**
```javascript
await db.query('BEGIN');
try {
  // ... all updates ...
  await db.query('COMMIT');
} catch (err) {
  await db.query('ROLLBACK');
  throw err;
}
```

#### **5. No Ledger Entries (Double-Entry Violation)**
**Problem:** Directly mutating `balance` column bypasses double-entry accounting. No audit trail exists.

**Why it matters in payments:** You cannot answer "where did this $100 come from?" Balance integrity cannot be verified. Regulatory audits will fail. If a bug corrupts balances, you cannot reconstruct the correct state.

**Fix:** Replace direct balance updates with ledger postings:
```javascript
// DEBIT clearing account, CREDIT recipient account
await insertLedgerEntry(txId, 'CLEARING_USD', 'DEBIT', amount);
await insertLedgerEntry(txId, 'CUSTOMER_USD', 'CREDIT', amount);
```

#### **6. Wrong Status Code for Unknown Transactions**
**Problem:** Returns **404** when `transaction not found`. Providers interpret 4xx as "don't retry."

**Why it matters in payments:** If the webhook arrives **before** the `/payments` response (race condition: provider's webhook is faster than your DB commit), returning 404 tells the provider to never retry. You'll never know the payment completed.

**Fix:**
```javascript
if (!transaction.rows[0]) {
  await logWebhookEvent(payload, signature, 'UNKNOWN_TX');
  return res.status(200).send('OK'); // Accept but don't process
}
```

#### **7. No State Transition Validation**
**Problem:** Allows invalid state transitions (e.g., `completed` → `failed`).

**Why it matters in payments:** A malicious or buggy webhook could reverse a settled payment days later, creating accounting chaos. Or transition directly from `initiated` to `completed`, skipping fraud checks.

**Fix:**
```javascript
const validTransitions = {
  'processing': ['completed', 'failed'],
  'completed': [],
  'failed': []
};

if (!validTransitions[currentStatus]?.includes(newStatus)) {
  // Log but return 200
  return res.status(200).send('OK');
}
```

#### **8. No Locked Balance Handling**
**Problem:** When initiating payment, funds should move from `available` → `locked`. On completion: `locked` → `0` (clearing). On failure: `locked` → `available` (reversal). This code only updates `available`, ignoring `locked`.

**Why it matters in payments:** Double-spending becomes possible. User can initiate payment (locking $100), then before completion, initiate another payment with the same $100 (since `available` wasn't actually decremented).

**Fix:** Track locked separately:
```javascript
// On initiate:
UPDATE accounts SET available = available - 100, locked = locked + 100 WHERE id = sender_id;

// On completed:
UPDATE accounts SET locked = locked - 100 WHERE id = sender_id;
UPDATE accounts SET available = available + 100 WHERE id = recipient_id;

// On failed:
UPDATE accounts SET locked = locked - 100, available = available + 100 WHERE id = sender_id;
```

#### **9. No Row Locking**
**Problem:** Concurrent webhooks for the same transaction (rare but possible) could cause race conditions.

**Why it matters in payments:** Two webhooks processing simultaneously could both pass the "check current status" step before either updates it, causing duplicate balance mutations.

**Fix:**
```javascript
const tx = await db.query(
  'SELECT * FROM transactions WHERE provider_reference = $1 FOR UPDATE',
  [payload.reference]
);
```

#### **10. Using `payload.amount` Instead of Stored Amount**
**Problem:** For `completed` status, uses `payload.amount` directly. Provider could send incorrect amount.

**Why it matters in payments:** If provider's webhook says "completed, amount: $1,000,000" but the original transaction was $100, you credit $1M.

**Fix:**
```javascript
// Always use stored transaction.amount, never payload.amount
await db.query('UPDATE accounts SET balance = balance + $1', [transaction.rows[0].amount]);
```

---

## 4B: Failure Scenarios

### 1. Double-Spend: Concurrent Payment Requests

**Implementation:** `PaymentService.java:56`
```java
AccountBalance bal = balanceRepo.lockByAccountAndCurrency(req.senderAccountId(), req.sourceCurrency())
        .orElseThrow(...);
```

The `@Lock(LockModeType.PESSIMISTIC_WRITE)` annotation on `AccountBalanceRepository.lockByAccountAndCurrency()` ensures that:

1. Request A acquires a database-level row lock on the sender's balance
2. Request B (concurrent, different idempotency key) **blocks** waiting for the lock
3. Request A checks balance, deducts funds, commits transaction, releases lock
4. Request B acquires lock, checks balance (now insufficient), throws exception

**What prevents overdraft:** The pessimistic lock + balance check happens in a single transaction. PostgreSQL's MVCC + row-level locking ensures serializable execution.

**Trade-off:** High contention on popular accounts (e.g., merchant with 100 concurrent payouts) will cause queue buildup. Could optimize with sharding or account-level semaphores, but for correctness, this is acceptable.

---

### 2. Webhook Arrives Before API Response

**Scenario:** Provider's webhook callback reaches `/webhooks/provider` before `POST /payments` has committed the transaction to the database.

**What happens:**
1. `POST /payments` begins transaction, inserts into `transactions` table (uncommitted)
2. Provider's systems are fast; webhook fires immediately
3. `/webhooks/provider` queries `transactions` by `provider_reference`
4. **Transaction not found** (uncommitted read isolation)

**Implementation:** `WebhookService.java:67-74`
```java
Optional<Transaction> txOpt = txRepo.findByProviderReference(ev.getProviderReference());
if (txOpt.isEmpty()) {
    ev.setProcessingStatus("PROCESSED");
    ev.setProcessedAt(Instant.now());
    webhookRepo.save(ev);
    return; // Return 200 anyway
}
```

**Outcome:** Webhook is logged but not processed. Returns **200 OK** to provider (so they don't retry). When the transaction eventually commits, status remains `PROCESSING`.

**Resolution path:**
- Operations team can manually trigger re-processing from `webhook_events` table
- Or implement a background job that scans for `PROCESSING` transactions older than 5 minutes and matches them against unprocessed webhooks

**Why 200 not 404:** Returning 404 would tell the provider "don't retry." We want them to retry (or we retry internally).

**Better fix:** Implement async webhook processing with a delay:
```java
// Enqueue webhook for processing 2 seconds later
webhookQueue.enqueueWithDelay(rawBody, signature, 2000);
```

---

### 3. FX Rate Stale

**Implementation:** `FxService.java:24-42`

The FX quote is created with:
```java
q.setExpiresAt(Instant.now().plusSeconds(ttlSeconds)); // Default 180s
```

**What should happen:**
1. User requests quote at T=0
2. User confirms at T=200s (past expiry)
3. System should **reject** and ask user to requote

**Current implementation flaw:** `PaymentService.java:64` creates a fresh quote on every payment initiation. The user never "locks" a quote in advance.

**If we implemented quote locking:** Add a check before payment initiation:
```java
if (quote.getExpiresAt().isBefore(Instant.now())) {
    throw new IllegalStateException("FX quote expired. Please requote.");
}
```

**Why it matters:** If market moves 3% against the user, they're protected by the locked rate. If market moves 3% in their favor, we (the platform) lose money. Expiry ensures we can re-hedge.

**Production approach:**
- Lock rate for 3 minutes
- If user doesn't confirm, quote expires
- Rate updates propagate from liquidity provider every 1 second
- Add `status: EXPIRED` to `fx_quotes` table; background job expires old quotes

---

### 4. Partial Settlement Reversal

**Scenario:** Payment shows `COMPLETED` on Day 1. Recipient's bank rejects the credit on Day 3 (e.g., account closed).

**Current implementation:** No mechanism for post-settlement reversals. `TransactionStateMachine.java:16` shows:
```java
"COMPLETED", List.of() // Terminal state, no outbound transitions
```

**How to model the reversal:**

**Option A: New transaction type**
```sql
INSERT INTO transactions (reference, type, parent_transaction_id, status, ...)
VALUES ('REV_12345', 'REVERSAL', original_tx_id, 'INITIATED', ...);
```

Ledger entries:
```sql
-- Original (Day 1):
DEBIT  CUST_LOCKED_USD     100
CREDIT PROVIDER_CLEARING   100
DEBIT  PROVIDER_CLEARING   100
CREDIT RECIPIENT_AVAILABLE 100

-- Reversal (Day 3):
DEBIT  RECIPIENT_AVAILABLE 100
CREDIT PROVIDER_CLEARING   100
DEBIT  PROVIDER_CLEARING   100
CREDIT SENDER_AVAILABLE    100  -- Return to sender
```

**Option B: Allow COMPLETED → REVERSED transition**
```java
"COMPLETED", List.of("REVERSED")
```

Then update the same transaction:
```java
if ("REVERSED".equals(next)) {
    ledgerService.postReversal(tx.getId(), tx.getDestinationCurrency(), tx.getDestinationAmount());
    // Debit recipient, credit sender
}
```

**Trade-off:** Option A preserves immutability of completed transactions (audit-friendly). Option B is simpler but makes the state machine more complex.

**Implementation choice:** Option A with `type` column differentiating `PAYMENT` vs `REVERSAL`.

---

### 5. Provider Timeout

**Scenario:** `ProviderClient.submitPayment()` times out after 30s. We don't know if the provider received the request.

**Current implementation:** `ProviderClient.java:11-14` is a stub (always succeeds). In production:

```java
try {
    String providerRef = httpClient.post("/payments", body, timeout=30s);
    return providerRef;
} catch (TimeoutException e) {
    // ??? What now?
}
```

**Options:**

**Bad approach:** Retry immediately → could create duplicate payment at provider.

**Correct approach:**

1. **Save idempotency key with provider request**:
```java
// Before submitting to provider
idempotencyRecord.setProviderRequestPayload(requestJson);
idempotencyRecord.save();
```

2. **Leave transaction in `INITIATED` state**:
```java
tx.setStatus("PROVIDER_SUBMIT_FAILED"); // or keep as INITIATED
tx.setErrorReason("Provider timeout");
txRepo.save(tx);
```

3. **Background reconciliation job**:
   - Every 5 minutes, query provider API: `GET /payments/{our_reference}`
   - If found: update our transaction with `provider_reference`, set status to `PROCESSING`
   - If not found after 1 hour: mark as `FAILED` and reverse lock

4. **Idempotent retry**:
   - If provider supports idempotency keys, include our transaction reference:
   ```java
   headers.put("Idempotency-Key", tx.getReference());
   ```
   - Retry 3 times with exponential backoff (1s, 5s, 15s)

**Why not just fail immediately?** The payment might have been submitted successfully. Failing prematurely means refunding the sender while the provider is processing — risking double-debit when the webhook arrives later.

**Production system:** Implement **dual-write pattern** or **outbox pattern**:
- Write to local DB + message queue atomically
- Worker drains queue and calls provider
- Provider call failures trigger retries with backoff
- Webhook arrival reconciles state

---

## 4C: Production Readiness

### 1. **Database Connection Pooling & Timeouts**

**Why it matters:** Under load, the application will exhaust database connections, causing request failures. Without query timeouts, a slow query (e.g., missing index) can block all connections, taking down the entire service.

**Failure mode prevented:** Cascading failure where a single slow query causes connection pool exhaustion → all requests fail → load balancer marks instance unhealthy → traffic shifts to remaining instances → they fail → total outage.

**Implementation:**
```yaml
# application.yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 5000
      idle-timeout: 300000
      max-lifetime: 600000
  jpa:
    properties:
      hibernate:
        query.timeout: 10 # seconds
        connection.provider_disables_autocommit: true # performance
```

**Monitoring:** Alert on:
- Connection pool utilization > 80%
- Query duration p99 > 1s
- Connection wait time > 100ms

---

### 2. **Retry Logic with Idempotency for Provider Calls**

**Why it matters:** Network is unreliable. Provider API calls will fail intermittently. Without retries, legitimate payments fail. Without idempotency, retries create duplicate payments.

**Failure mode prevented:**
- User initiates $10k payment
- Provider API returns 503 (temporary overload)
- We fail the payment
- User retries manually
- Now two $10k payments are in flight

**Implementation:**
```java
@Retryable(
  value = {RestClientException.class},
  maxAttempts = 3,
  backoff = @Backoff(delay = 1000, multiplier = 2)
)
public String submitPayment(String txReference, PaymentRequest req) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Idempotency-Key", txReference); // Provider must honor this

    ResponseEntity<ProviderResponse> resp = restTemplate.exchange(
        providerUrl, HttpMethod.POST, new HttpEntity<>(req, headers), ProviderResponse.class
    );

    return resp.getBody().getProviderReference();
}
```

**Dead letter queue:** After 3 failures, publish to DLQ for manual investigation.

---

### 3. **Rate Limiting (Per-Account & Global)**

**Why it matters:** Prevents:
- Accidental DoS from buggy client code (infinite loop calling API)
- Malicious abuse (attacker creating millions of $0.01 payments to overwhelm system)
- Compromised API keys being used to drain accounts

**Failure mode prevented:**
- Buggy merchant integration calls `POST /payments` 10,000 times/sec
- Database connections exhausted
- All merchants unable to process payments

**Implementation:**
```java
@RateLimiter(name = "paymentApi", fallbackMethod = "rateLimitFallback")
@PostMapping
public PaymentResponse createPayment(...) { ... }

// application.yaml
resilience4j.ratelimiter:
  instances:
    paymentApi:
      limitForPeriod: 100 # requests
      limitRefreshPeriod: 1s
      timeoutDuration: 0s # fail immediately, don't queue
```

**Per-account limiting:**
```java
String key = "payment:limit:" + req.senderAccountId();
Long count = redisTemplate.opsForValue().increment(key);
redisTemplate.expire(key, 60, TimeUnit.SECONDS);

if (count > 100) {
    throw new RateLimitExceededException("Max 100 payments per minute");
}
```

---

### 4. **Structured Logging + Distributed Tracing**

**Why it matters:** When a payment fails in production, you need to answer:
- What was the exact request payload?
- What was the database state at that moment?
- Did the provider API call succeed?
- If there were retries, what were the responses?

Without structured logs, debugging is impossible. Without trace IDs, you can't correlate logs across services.

**Failure mode prevented:**
- Customer: "My $50k payment disappeared!"
- Engineer: *searches logs for "50000"* → finds nothing (amount was logged as "50000.00")
- 2 hours wasted grepping logs
- Finally find it with transaction ID
- Logs show "Payment initiated" but no webhook received
- Check provider dashboard manually
- Find payment stuck in "pending"
- Could have been resolved in 5 minutes with proper tracing

**Implementation:**
```java
// Add to all service methods
MDC.put("transactionId", tx.getId().toString());
MDC.put("traceId", UUID.randomUUID().toString());

log.info("Payment initiated",
    kv("senderAccountId", req.senderAccountId()),
    kv("amount", req.sourceAmount()),
    kv("currency", req.sourceCurrency()),
    kv("providerRef", providerRef)
);
```

**Tooling:**
- ELK Stack (Elasticsearch + Logstash + Kibana)
- Or OpenTelemetry + Jaeger for distributed tracing
- Include trace ID in API responses so customer support can debug

---

### 5. **Background Reconciliation Job**

**Why it matters:** Distributed systems have edge cases that cause state divergence:
- Webhook never arrives (provider bug)
- Webhook arrives but processing fails mid-transaction
- Database corruption (rare but happens)
- Clock skew causes timing bugs

**Failure mode prevented:**
- Payment stuck in `PROCESSING` for 3 days
- User calls support: "Where's my money?"
- Support has no visibility
- Manual SQL queries required to diagnose

**Implementation:**
```java
@Scheduled(cron = "0 */10 * * * *") // Every 10 minutes
public void reconcileStuckTransactions() {
    List<Transaction> stuck = txRepo.findByStatusAndCreatedAtBefore(
        "PROCESSING", Instant.now().minus(30, ChronoUnit.MINUTES)
    );

    for (Transaction tx : stuck) {
        // Query provider API for actual status
        ProviderTransaction providerTx = providerClient.getTransaction(tx.getProviderReference());

        if (!providerTx.getStatus().equals(tx.getStatus())) {
            log.warn("Reconciliation: status mismatch",
                kv("txId", tx.getId()),
                kv("ourStatus", tx.getStatus()),
                kv("providerStatus", providerTx.getStatus())
            );

            // Trigger state transition
            webhookService.handleStatusUpdate(tx, providerTx.getStatus());
        }
    }
}
```

**Daily reconciliation:**
- Compare sum of all ledger entries = 0 (double-entry invariant)
- Compare account balances with sum of related ledger entries
- Alert if discrepancies found

---

## Summary

The codebase demonstrates solid foundational understanding of payment systems:
- Double-entry ledger design
- Idempotency handling
- State machine for transaction lifecycle
- Webhook signature verification

**Critical gaps addressed:**
- Ledger integration (was commented out, now functional)
- Webhook settlement/reversal logic (was TODO, now implemented)
- Pagination and filtering APIs (were missing, now added)

**Production readiness requires:**
1. Operational resilience (connection pooling, retries, rate limiting)
2. Observability (logging, tracing, metrics)
3. Data integrity (reconciliation jobs, background monitoring)
4. Failure recovery (manual admin tools, runbooks)

This implementation would be suitable for internal testing or MVP with manual oversight. For production with real money, the 5 items in Section 4C are non-negotiable.
