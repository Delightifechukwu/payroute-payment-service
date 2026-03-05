# PayRoute - Cross-Border Payment Processing System

A full-stack payment processing platform built with **Spring Boot**, **React**, and **PostgreSQL** that handles cross-border payments with FX conversion, double-entry ledger accounting, and asynchronous provider integration.

---

## Features

- ✅ Multi-currency account balances with available/locked funds tracking
- ✅ FX quote generation with expiry (NGN → USD/EUR/GBP)
- ✅ Idempotent payment initiation API
- ✅ Double-entry ledger bookkeeping for all financial transactions
- ✅ Asynchronous webhook processing with HMAC signature verification
- ✅ Transaction state machine with valid state transitions only
- ✅ Pessimistic locking to prevent double-spend attacks
- ✅ React dashboard for operations team (transaction list, payment initiation, balances)
- ✅ Pagination and filtering for transaction queries

---

## Tech Stack

### Backend
- **Java 21** with **Spring Boot 4.0.3**
- **PostgreSQL 16** with Flyway migrations
- **JPA/Hibernate** for ORM
- **Maven** for dependency management

### Frontend
- **React 18** with **Vite**
- **React Router** for navigation
- **Axios** for API calls

### Infrastructure
- **Docker Compose** for orchestration
- **Flyway** for database migrations

---

## Quick Start

### Prerequisites
- Docker & Docker Compose installed
- Ports 5432 (PostgreSQL), 8080 (Backend), 5173 (Frontend) available

### Running with Docker Compose

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd payroute-takehome
   ```

2. **Set environment variables** (optional)
   ```bash
   cp .env.example .env
   # Edit .env with your configuration
   ```

3. **Start all services**
   ```bash
   docker-compose up --build
   ```

4. **Access the application**
   - Frontend: http://localhost:5173
   - Backend API: http://localhost:8080
   - Swagger UI: http://localhost:8080/swagger-ui.html

5. **Seed data**
   - Two test accounts are automatically created:
     - Sender: `11111111-1111-1111-1111-111111111111` (USD: $10,000, NGN: ₦5,000,000)
     - Recipient: `22222222-2222-2222-2222-222222222222` (EUR: €1,000, USD: $500)

---

## Manual Setup (Without Docker)

### Backend

1. **Start PostgreSQL**
   ```bash
   psql -U postgres
   CREATE DATABASE payroute;
   ```

2. **Configure application**
   ```bash
   # Edit src/main/resources/application.yaml
   # Update database credentials if needed
   ```

3. **Run the backend**
   ```bash
   ./mvnw spring-boot:run
   ```

   Backend will start on `http://localhost:8080`

### Frontend

1. **Install dependencies**
   ```bash
   cd frontend
   npm install
   ```

2. **Start development server**
   ```bash
   npm run dev
   ```

   Frontend will start on `http://localhost:5173`

---

## API Endpoints

### Payment Operations

#### **POST /payments**
Initiate a cross-border payment.

**Headers:**
- `Idempotency-Key`: String (required) - Ensures duplicate requests return the same response

**Request Body:**
```json
{
  "senderAccountId": "11111111-1111-1111-1111-111111111111",
  "recipientAccountId": "22222222-2222-2222-2222-222222222222",
  "sourceCurrency": "USD",
  "destinationCurrency": "EUR",
  "sourceAmount": 100.00
}
```

**Response:**
```json
{
  "reference": "PR_abc-123",
  "status": "PROCESSING",
  "sourceAmount": 100.00,
  "destinationAmount": 92.00,
  "fxRate": 0.92
}
```

#### **GET /payments?status={status}&page={page}&size={size}**
List payments with pagination and filtering.

**Query Parameters:**
- `status` (optional): Filter by status (INITIATED, PROCESSING, COMPLETED, FAILED)
- `startDate` (optional): ISO 8601 timestamp
- `endDate` (optional): ISO 8601 timestamp
- `page` (default: 0): Page number
- `size` (default: 20): Page size

**Response:**
```json
{
  "content": [ /* array of transactions */ ],
  "totalPages": 5,
  "totalElements": 100,
  "number": 0,
  "size": 20
}
```

#### **GET /payments/{reference}**
Get detailed transaction information including ledger entries and status history.

**Response:**
```json
{
  "id": "...",
  "reference": "PR_abc-123",
  "status": "COMPLETED",
  "sourceAmount": 100.00,
  "destinationAmount": 92.00,
  "fxRate": 0.92,
  "statusHistory": [
    {
      "fromStatus": "INITIATED",
      "toStatus": "PROCESSING",
      "reason": "submitted_to_provider",
      "atTime": "2025-01-15T10:00:00Z"
    }
  ],
  "ledgerEntries": [
    {
      "ledgerAccountCode": "LEDGER_...",
      "direction": "DEBIT",
      "amount": 100.00,
      "currency": "USD",
      "createdAt": "2025-01-15T10:00:00Z"
    }
  ]
}
```

### Webhook Operations

#### **POST /webhooks**
Receive payment status updates from downstream provider.

**Headers:**
- `X-Webhook-Signature`: HMAC-SHA256(secret, raw_body)

**Request Body:**
```json
{
  "reference": "prov_xyz-789",
  "status": "completed"
}
```

**Response:** `200 OK` (always, even for invalid signatures or unknown transactions)

### Utility Endpoints

#### **GET /balances**
List all account balances.

#### **GET /transactions**
Legacy endpoint for listing transactions (use `/payments` instead).

#### **GET /transactions/{reference}**
Legacy endpoint for transaction details (use `/payments/{reference}` instead).

---

## Testing the System

### 1. Create a Payment
```bash
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-key-123" \
  -d '{
    "senderAccountId": "11111111-1111-1111-1111-111111111111",
    "recipientAccountId": "22222222-2222-2222-2222-222222222222",
    "sourceCurrency": "USD",
    "destinationCurrency": "EUR",
    "sourceAmount": 100
  }'
```

### 2. Simulate Provider Webhook (Complete Payment)
```bash
# Get the providerReference from the payment response above
export PROVIDER_REF="prov_..."
export RAW_BODY='{"reference":"'$PROVIDER_REF'","status":"completed"}'
export SIGNATURE=$(echo -n "$RAW_BODY" | openssl dgst -sha256 -hmac "dev_secret_change_me" | awk '{print $2}')

curl -X POST http://localhost:8080/webhooks \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Signature: $SIGNATURE" \
  -d "$RAW_BODY"
```

### 3. Check Payment Status
```bash
curl http://localhost:8080/payments/{reference}
```

### 4. View Balances
```bash
curl http://localhost:8080/balances
```

---

## Database Schema

The system uses a fully normalized PostgreSQL schema with:

- **accounts**: Customer accounts
- **account_balances**: Multi-currency balances (available + locked)
- **transactions**: Payment lifecycle tracking
- **transaction_status_history**: Audit trail of status changes
- **fx_quotes**: FX rate snapshots with expiry
- **ledger_accounts**: Chart of accounts (LIABILITY, ASSET, etc.)
- **ledger_postings**: Idempotent grouping of ledger entries
- **ledger_entries**: Double-entry bookkeeping (DEBIT/CREDIT)
- **webhook_events**: Raw webhook log with fingerprint-based deduplication
- **idempotency_records**: API request deduplication

See `SCHEMA_DESIGN.md` for detailed design rationale.

---

## Architecture Decisions

### 1. Idempotency
- All `POST /payments` requests require an `Idempotency-Key` header
- Duplicate keys return the cached response without re-executing logic
- Prevents accidental duplicate payments from network retries

### 2. Double-Entry Ledger
- Every transaction creates balanced ledger entries (DEBIT amount = CREDIT amount)
- Enables audit trail and balance reconciliation
- Ledger postings are idempotent via `unique_key` constraint

### 3. Pessimistic Locking
- `AccountBalanceRepository.lockByAccountAndCurrency()` uses `FOR UPDATE`
- Prevents concurrent requests from overdrawing an account
- Trade-off: Lower throughput under high contention

### 4. Webhook Idempotency
- Webhooks are logged with a fingerprint (SHA256 of raw body + provider_reference)
- Duplicate webhooks are silently ignored
- Always returns `200 OK` to prevent provider retry storms

### 5. State Machine
- Valid transitions enforced: `INITIATED → PROCESSING → [COMPLETED | FAILED]`
- Invalid transitions (e.g., `COMPLETED → FAILED`) are rejected

---

## Known Limitations / Future Improvements

1. **No async processing**: Webhook handling is synchronous. Production should use message queue (RabbitMQ, Kafka).
2. **No reconciliation job**: Stuck transactions (provider never sends webhook) require manual intervention.
3. **No rate limiting**: API can be overwhelmed by high request volumes.
4. **Simplified FX quotes**: Real system would integrate with external FX provider (e.g., Bloomberg, Oanda).
5. **No role-based access control**: All API endpoints are public.
6. **No multi-tenancy**: Single namespace for all accounts.
7. **No fraud detection**: No velocity checks, AML screening, or sanctions lists.
8. **No partial settlement handling**: Cannot handle scenario where provider completes 80% of payment.

---

## Project Structure

```
payroute-takehome/
├── src/main/java/com/payrout/backend/
│   ├── controller/       # REST API endpoints
│   ├── service/          # Business logic
│   ├── repository/       # JPA repositories
│   ├── domain/           # Entity models
│   ├── dto/              # Request/response DTOs
│   ├── state/            # State machine
│   ├── config/           # Spring configuration
│   └── util/             # Crypto & hashing utilities
├── src/main/resources/
│   ├── db/migration/     # Flyway SQL migrations
│   ├── application.yaml  # Spring Boot config
│   └── data.sql          # Seed data
├── frontend/
│   ├── src/
│   │   ├── pages/        # React components
│   │   └── api/          # API client
│   └── package.json
├── docker-compose.yml    # Docker orchestration
├── Dockerfile            # Backend container
├── ANALYSIS.md           # Technical analysis & code review
├── SCHEMA_DESIGN.md      # Database schema design doc
└── README.md             # This file
```

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/payroute` | PostgreSQL JDBC URL |
| `DB_USER` | `postgres` | Database username |
| `DB_PASSWORD` | `postgres` | Database password |
| `WEBHOOK_SECRET` | `dev_secret_change_me` | HMAC secret for webhook signature verification |
| `FX_QUOTE_TTL_SECONDS` | `180` | FX quote expiry time (3 minutes) |

---

## License

This is a take-home assignment project. Not licensed for production use.

---

## Contact

For questions about this implementation, please refer to the `ANALYSIS.md` document which contains detailed explanations of design decisions and trade-offs.
