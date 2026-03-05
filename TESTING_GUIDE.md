# PayRoute Testing Guide

Complete guide for testing the PayRoute payment system with Postman and other tools.

---

## Table of Contents
1. [Running the Application](#running-the-application)
2. [Postman Setup](#postman-setup)
3. [Test Scenarios](#test-scenarios)
4. [Webhook Testing](#webhook-testing)
5. [Troubleshooting](#troubleshooting)

---

## Running the Application

### Option 1: Docker Compose (Recommended)

**Prerequisites:**
- Docker and Docker Compose installed

**Steps:**

1. **Navigate to project directory**
   ```bash
   cd payroute-takehome
   ```

2. **Start all services**
   ```bash
   docker-compose up --build
   ```

   This will start:
   - PostgreSQL on port 5432
   - Backend API on port 8080
   - Frontend UI on port 5173

3. **Wait for services to be ready**
   - Look for: `Started PayrouteTakehomeApplication` in logs
   - Backend: http://localhost:8080
   - Frontend: http://localhost:5173
   - Swagger UI: http://localhost:8080/swagger-ui.html

4. **Stop services**
   ```bash
   docker-compose down
   ```

5. **Clean restart (remove database)**
   ```bash
   docker-compose down -v
   docker-compose up --build
   ```

---

### Option 2: Manual Setup (If Java 21 is properly configured)

**Prerequisites:**
- Java 21 JDK installed and JAVA_HOME set
- PostgreSQL 16 running
- Node.js 20+ installed

**Backend:**
```bash
# Create database
psql -U postgres
CREATE DATABASE payroute;
\q

# Run backend
./mvnw spring-boot:run
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev
```

---

## Postman Setup

### Import Postman Collection

Create a new collection in Postman with the following requests:

### 1. **Create Payment**

**Request:**
- Method: `POST`
- URL: `http://localhost:8080/payments`
- Headers:
  ```
  Content-Type: application/json
  Idempotency-Key: {{$guid}}
  ```
- Body (raw JSON):
  ```json
  {
    "senderAccountId": "11111111-1111-1111-1111-111111111111",
    "recipientAccountId": "22222222-2222-2222-2222-222222222222",
    "sourceCurrency": "USD",
    "destinationCurrency": "EUR",
    "sourceAmount": 100
  }
  ```

**Expected Response (201 Created):**
```json
{
  "reference": "PR_abc-123-...",
  "status": "PROCESSING",
  "sourceAmount": 100.00,
  "destinationAmount": 92.00,
  "fxRate": 0.92
}
```

**Save the `reference` value for next requests!**

---

### 2. **Get Payment Details**

**Request:**
- Method: `GET`
- URL: `http://localhost:8080/payments/{{reference}}`
  - Replace `{{reference}}` with the value from step 1

**Expected Response:**
```json
{
  "id": "...",
  "reference": "PR_abc-123-...",
  "senderAccountId": "11111111-1111-1111-1111-111111111111",
  "recipientAccountId": "22222222-2222-2222-2222-222222222222",
  "sourceCurrency": "USD",
  "destinationCurrency": "EUR",
  "sourceAmount": 100.00,
  "destinationAmount": 92.00,
  "status": "PROCESSING",
  "providerReference": "prov_...",
  "fxRate": 0.92,
  "createdAt": "2025-01-15T10:00:00Z",
  "updatedAt": "2025-01-15T10:00:01Z",
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
      "id": "...",
      "ledgerAccountCode": "LEDGER_...",
      "currency": "USD",
      "amount": 100.00,
      "direction": "DEBIT",
      "createdAt": "2025-01-15T10:00:00Z"
    },
    {
      "id": "...",
      "ledgerAccountCode": "LEDGER_...",
      "currency": "USD",
      "amount": 100.00,
      "direction": "CREDIT",
      "createdAt": "2025-01-15T10:00:00Z"
    }
  ]
}
```

---

### 3. **List Payments (Paginated)**

**Request:**
- Method: `GET`
- URL: `http://localhost:8080/payments?page=0&size=20&status=PROCESSING`

**Query Parameters:**
- `page` (optional): Page number (default: 0)
- `size` (optional): Items per page (default: 20)
- `status` (optional): Filter by status (INITIATED, PROCESSING, COMPLETED, FAILED)
- `startDate` (optional): ISO 8601 timestamp
- `endDate` (optional): ISO 8601 timestamp

**Expected Response:**
```json
{
  "content": [
    {
      "id": "...",
      "reference": "PR_abc-123-...",
      "status": "PROCESSING",
      "sourceAmount": 100.00,
      "destinationAmount": 92.00,
      "sourceCurrency": "USD",
      "destinationCurrency": "EUR",
      "createdAt": "2025-01-15T10:00:00Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalPages": 1,
  "totalElements": 1,
  "last": true,
  "first": true
}
```

---

### 4. **Get All Balances**

**Request:**
- Method: `GET`
- URL: `http://localhost:8080/balances`

**Expected Response:**
```json
[
  {
    "id": "...",
    "accountId": "11111111-1111-1111-1111-111111111111",
    "currency": "USD",
    "available": 9900.00,
    "locked": 100.00,
    "updatedAt": "2025-01-15T10:00:00Z"
  },
  {
    "id": "...",
    "accountId": "11111111-1111-1111-1111-111111111111",
    "currency": "NGN",
    "available": 5000000.00,
    "locked": 0.00,
    "updatedAt": "2025-01-15T09:00:00Z"
  }
]
```

---

### 5. **Complete Payment (Webhook Simulation)**

**Request:**
- Method: `POST`
- URL: `http://localhost:8080/webhooks`
- Headers:
  ```
  Content-Type: application/json
  X-Webhook-Signature: <calculated_hmac>
  ```
- Body (raw JSON):
  ```json
  {
    "reference": "prov_...",
    "status": "completed"
  }
  ```

**Important:** Replace `prov_...` with the `providerReference` from step 2!

**Calculating the HMAC Signature:**

You need to calculate HMAC-SHA256 of the raw body using the webhook secret.

**Using Postman Pre-request Script:**

Add this to the Pre-request Script tab:
```javascript
const CryptoJS = require('crypto-js');
const secret = 'dev_secret_change_me';
const body = pm.request.body.raw;
const signature = CryptoJS.HmacSHA256(body, secret).toString(CryptoJS.enc.Hex);
pm.request.headers.add({key: 'X-Webhook-Signature', value: signature});
```

**Using Command Line (alternative):**

On Windows PowerShell:
```powershell
$secret = "dev_secret_change_me"
$body = '{"reference":"prov_123","status":"completed"}'
$hmac = New-Object System.Security.Cryptography.HMACSHA256
$hmac.Key = [Text.Encoding]::UTF8.GetBytes($secret)
$hash = $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($body))
$signature = [BitConverter]::ToString($hash).Replace('-','').ToLower()
Write-Output $signature
```

On Linux/Mac:
```bash
echo -n '{"reference":"prov_123","status":"completed"}' | openssl dgst -sha256 -hmac "dev_secret_change_me" | awk '{print $2}'
```

**Expected Response:**
```
OK
```

---

### 6. **Fail Payment (Webhook Simulation)**

**Request:**
- Method: `POST`
- URL: `http://localhost:8080/webhooks`
- Headers:
  ```
  Content-Type: application/json
  X-Webhook-Signature: <calculated_hmac>
  ```
- Body (raw JSON):
  ```json
  {
    "reference": "prov_...",
    "status": "failed"
  }
  ```

**Expected Response:**
```
OK
```

After this, check the payment status - it should be `FAILED` and sender's balance should be restored.

---

## Test Scenarios

### Scenario 1: Successful Payment Flow

1. **Check initial balances**
   ```
   GET /balances
   ```
   Note sender USD available = 10000

2. **Create payment**
   ```
   POST /payments
   Body: 100 USD → EUR
   ```
   Check sender balance: available = 9900, locked = 100

3. **Simulate provider completion**
   ```
   POST /webhooks
   Body: {"reference": "prov_xxx", "status": "completed"}
   ```

4. **Verify final state**
   ```
   GET /payments/{reference}
   ```
   - Status = COMPLETED
   - Sender: locked = 0
   - Recipient: available increased by 92 EUR

---

### Scenario 2: Failed Payment Flow

1. **Create payment**
   ```
   POST /payments
   Body: 100 USD → EUR
   ```

2. **Simulate provider failure**
   ```
   POST /webhooks
   Body: {"reference": "prov_xxx", "status": "failed"}
   ```

3. **Verify reversal**
   ```
   GET /balances
   ```
   - Sender: available = 10000 (restored)
   - Sender: locked = 0

---

### Scenario 3: Idempotency Test

1. **Create payment with specific key**
   ```
   POST /payments
   Header: Idempotency-Key: test-key-123
   Body: 100 USD → EUR
   ```
   Note the reference and balance changes

2. **Retry with same key**
   ```
   POST /payments
   Header: Idempotency-Key: test-key-123
   Body: 100 USD → EUR
   ```

3. **Verify:**
   - Returns same reference
   - Balance NOT debited twice
   - Only ONE transaction created

---

### Scenario 4: Insufficient Balance

1. **Create large payment**
   ```
   POST /payments
   Body: 50000 USD → EUR (more than available)
   ```

2. **Expected:**
   - HTTP 500 Internal Server Error
   - Message: "Insufficient balance"

---

### Scenario 5: Invalid Webhook Signature

1. **Send webhook with wrong signature**
   ```
   POST /webhooks
   Header: X-Webhook-Signature: invalid_sig
   Body: {"reference": "prov_123", "status": "completed"}
   ```

2. **Expected:**
   - HTTP 200 OK (to prevent retry)
   - Transaction status UNCHANGED
   - Check logs for "invalid_signature" error

---

## Webhook Testing

### Testing with Postman

**Step 1: Create payment and get provider reference**
```
POST /payments
→ Save providerReference from response
```

**Step 2: Add Pre-request Script to webhook request**

In Postman, go to the webhook request → Pre-request Script tab:

```javascript
// Webhook body (must match exactly)
const body = pm.request.body.raw;

// Secret (must match application.yaml)
const secret = 'dev_secret_change_me';

// Calculate HMAC-SHA256
const CryptoJS = require('crypto-js');
const signature = CryptoJS.HmacSHA256(body, secret).toString(CryptoJS.enc.Hex);

// Set header
pm.request.headers.upsert({
    key: 'X-Webhook-Signature',
    value: signature
});

console.log('Generated signature:', signature);
```

**Step 3: Send webhook**
```
POST /webhooks
Body: {"reference":"prov_xxx","status":"completed"}
```

The signature will be auto-calculated!

---

### Testing with cURL

**Complete Payment:**
```bash
# Step 1: Set variables
PROVIDER_REF="prov_abc123"
STATUS="completed"
WEBHOOK_SECRET="dev_secret_change_me"

# Step 2: Create body
BODY="{\"reference\":\"$PROVIDER_REF\",\"status\":\"$STATUS\"}"

# Step 3: Calculate signature (Linux/Mac)
SIGNATURE=$(echo -n "$BODY" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET" | awk '{print $2}')

# Step 4: Send request
curl -X POST http://localhost:8080/webhooks \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Signature: $SIGNATURE" \
  -d "$BODY"
```

**Windows PowerShell:**
```powershell
$providerRef = "prov_abc123"
$status = "completed"
$secret = "dev_secret_change_me"
$body = "{`"reference`":`"$providerRef`",`"status`":`"$status`"}"

$hmac = New-Object System.Security.Cryptography.HMACSHA256
$hmac.Key = [Text.Encoding]::UTF8.GetBytes($secret)
$hash = $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($body))
$signature = [BitConverter]::ToString($hash).Replace('-','').ToLower()

Invoke-RestMethod -Uri "http://localhost:8080/webhooks" `
  -Method POST `
  -Headers @{
    "Content-Type"="application/json"
    "X-Webhook-Signature"=$signature
  } `
  -Body $body
```

---

## Troubleshooting

### Issue: "Connection refused" to localhost:8080

**Solution:**
- Check if backend is running: `docker ps`
- Check logs: `docker-compose logs backend`
- Restart: `docker-compose restart backend`

---

### Issue: "Transaction not found" in webhook

**Cause:** Using wrong provider reference

**Solution:**
1. Get payment details: `GET /payments/{reference}`
2. Copy the exact `providerReference` value
3. Use that in webhook body

---

### Issue: Database connection error

**Solution:**
```bash
# Check if PostgreSQL is running
docker ps | grep postgres

# Restart database
docker-compose restart postgres

# Reset database
docker-compose down -v
docker-compose up -d postgres
```

---

### Issue: Signature verification failed

**Cause:** Mismatch between body and signature

**Solution:**
1. Ensure body is EXACTLY the same (no extra spaces/newlines)
2. Check secret matches `application.yaml`
3. Use the Pre-request Script in Postman
4. Verify with: `System.out.println("EXPECTED SIGNATURE: " + expected);` in logs

---

### Issue: "Insufficient balance"

**Cause:** Account balance too low

**Solution:**
```sql
-- Connect to database
docker exec -it payroute-db psql -U postgres -d payroute

-- Check balances
SELECT * FROM account_balances;

-- Add funds (for testing)
UPDATE account_balances
SET available = 100000
WHERE account_id = '11111111-1111-1111-1111-111111111111'
AND currency = 'USD';
```

---

## Advanced Testing

### Load Testing with Apache Bench

```bash
# Install Apache Bench
# Ubuntu: sudo apt-get install apache2-utils
# Mac: brew install httpd

# Create payment request file
cat > payment.json <<EOF
{
  "senderAccountId": "11111111-1111-1111-1111-111111111111",
  "recipientAccountId": "22222222-2222-2222-2222-222222222222",
  "sourceCurrency": "USD",
  "destinationCurrency": "EUR",
  "sourceAmount": 1
}
EOF

# Send 100 requests with 10 concurrent
ab -n 100 -c 10 -T 'application/json' \
  -H 'Idempotency-Key: load-test-1' \
  -p payment.json \
  http://localhost:8080/payments
```

---

## Postman Environment Variables

Create a Postman environment with:

```json
{
  "baseUrl": "http://localhost:8080",
  "senderAccountId": "11111111-1111-1111-1111-111111111111",
  "recipientAccountId": "22222222-2222-2222-2222-222222222222",
  "webhookSecret": "dev_secret_change_me"
}
```

Then use `{{baseUrl}}`, `{{senderAccountId}}` in requests!

---

## Next Steps

1. **Open Swagger UI**: http://localhost:8080/swagger-ui.html
2. **Try the frontend**: http://localhost:5173
3. **Check database**: `docker exec -it payroute-db psql -U postgres -d payroute`
4. **View logs**: `docker-compose logs -f backend`

---

## Summary

✅ **Create Payment**: POST /payments with Idempotency-Key
✅ **View Details**: GET /payments/{reference}
✅ **List All**: GET /payments?status=PROCESSING
✅ **Check Balances**: GET /balances
✅ **Complete Payment**: POST /webhooks (with HMAC signature)
✅ **Fail Payment**: POST /webhooks (status: failed)

Happy Testing! 🚀
