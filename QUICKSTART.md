# PayRoute - Quick Start Guide

Get PayRoute up and running in 5 minutes!

---

## Prerequisites

- **Docker Desktop** installed and running
- **Postman** (optional, for API testing)

That's it! Docker handles everything else.

---

## Step 1: Start the Application

### Windows:
Double-click `start.bat` or run:
```cmd
start.bat
```

### Mac/Linux:
```bash
docker-compose up --build -d
```

**Wait ~30 seconds** for services to start.

---

## Step 2: Verify Services are Running

Open in browser:
- **Frontend**: http://localhost:5173
- **Swagger API Docs**: http://localhost:8080/swagger-ui.html

Or check with:
```bash
docker-compose ps
```

Should show:
- `payroute-backend` (running)
- `payroute-frontend` (running)
- `payroute-db` (running)

---

## Step 3: Test via Frontend (Easiest)

1. **Open**: http://localhost:5173
2. **Click**: "Create Payment" tab
3. **Fill form**:
   - Source: USD
   - Destination: EUR
   - Amount: 100
4. **Click**: "Create Payment"
5. **View result**: Reference number and status

---

## Step 4: Test via Postman

### Import Collection

1. Open Postman
2. Click **Import**
3. Select file: `PayRoute.postman_collection.json`
4. Collection "PayRoute API" appears

### Run Requests in Order

**Request 1: Create Payment**
- Click "1. Create Payment"
- Click **Send**
- ✅ Returns: `reference`, `status: PROCESSING`
- 📝 Note the `providerReference` value!

**Request 2: Get Payment Details**
- Click "2. Get Payment Details"
- Click **Send**
- ✅ Shows full transaction with ledger entries

**Request 3: Complete Payment (Webhook)**
- Click "6. Complete Payment (Webhook)"
- **Verify** the body has correct `providerReference`
- Click **Send**
- ✅ Returns: `OK`

**Request 4: Check Status**
- Re-run "2. Get Payment Details"
- ✅ Status should now be `COMPLETED`

**Request 5: Check Balances**
- Click "5. Get All Balances"
- Click **Send**
- ✅ Sender USD: 9900, Recipient EUR: +92

---

## Step 5: Test via cURL

### Create Payment
```bash
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-123" \
  -d '{
    "senderAccountId": "11111111-1111-1111-1111-111111111111",
    "recipientAccountId": "22222222-2222-2222-2222-222222222222",
    "sourceCurrency": "USD",
    "destinationCurrency": "EUR",
    "sourceAmount": 100
  }'
```

### Get Payment Details
```bash
curl http://localhost:8080/payments/PR_abc-123-...
```

### Check Balances
```bash
curl http://localhost:8080/balances
```

---

## Common Issues & Solutions

### Issue: "Connection refused" to localhost:8080

**Solution:**
```bash
# Check if backend is running
docker-compose logs backend

# Restart backend
docker-compose restart backend
```

### Issue: Frontend shows "Network Error"

**Solution:**
```bash
# Check if all services are up
docker-compose ps

# Restart all
docker-compose restart
```

### Issue: Database errors

**Solution:**
```bash
# Reset database
docker-compose down -v
docker-compose up -d
```

---

## Stopping the Application

```bash
docker-compose down
```

To also remove database data:
```bash
docker-compose down -v
```

---

## Default Test Accounts

**Sender Account:**
- ID: `11111111-1111-1111-1111-111111111111`
- Balances:
  - USD: $10,000
  - NGN: ₦5,000,000

**Recipient Account:**
- ID: `22222222-2222-2222-2222-222222222222`
- Balances:
  - EUR: €1,000
  - USD: $500

---

## Key Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/payments` | Create payment |
| GET | `/payments/{ref}` | Get payment details |
| GET | `/payments` | List payments (paginated) |
| GET | `/balances` | Get all balances |
| POST | `/webhooks` | Webhook callback |

---

## Next Steps

1. **Read**: `TESTING_GUIDE.md` for detailed API testing
2. **Read**: `ANALYSIS.md` for technical design explanations
3. **Read**: `SCHEMA_DESIGN.md` for database schema docs
4. **Explore**: Swagger UI at http://localhost:8080/swagger-ui.html

---

## Quick Test Workflow

```bash
# 1. Start services
docker-compose up -d

# 2. Create payment
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-1" \
  -d '{"senderAccountId":"11111111-1111-1111-1111-111111111111","recipientAccountId":"22222222-2222-2222-2222-222222222222","sourceCurrency":"USD","destinationCurrency":"EUR","sourceAmount":100}'

# 3. List payments
curl http://localhost:8080/payments

# 4. Check balances
curl http://localhost:8080/balances

# 5. View logs
docker-compose logs -f backend

# 6. Stop
docker-compose down
```

---

## Troubleshooting

### View Logs
```bash
# All services
docker-compose logs

# Backend only
docker-compose logs backend

# Follow logs (live)
docker-compose logs -f
```

### Access Database
```bash
docker exec -it payroute-db psql -U postgres -d payroute

# Useful queries:
SELECT * FROM transactions;
SELECT * FROM account_balances;
SELECT * FROM ledger_entries;
```

### Reset Everything
```bash
docker-compose down -v
docker system prune -f
docker-compose up --build -d
```

---

## Support

If you encounter issues:

1. Check logs: `docker-compose logs`
2. Verify Docker is running: `docker ps`
3. Check ports are free: `netstat -an | findstr "8080\|5432\|5173"`
4. Review `TESTING_GUIDE.md` for detailed troubleshooting

---

**You're all set! Happy testing! 🚀**
