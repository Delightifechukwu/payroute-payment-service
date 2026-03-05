-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Accounts
CREATE TABLE accounts (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          name TEXT NOT NULL,
                          created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Multi-currency balances (available + locked)
CREATE TABLE account_balances (
                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  account_id UUID NOT NULL REFERENCES accounts(id),
                                  currency TEXT NOT NULL,
                                  available NUMERIC(18,2) NOT NULL CHECK (available >= 0),
                                  locked NUMERIC(18,2) NOT NULL CHECK (locked >= 0),
                                  updated_at TIMESTAMP NOT NULL DEFAULT now(),
                                  CONSTRAINT uq_balance_account_currency UNIQUE (account_id, currency)
);

-- FX quotes
CREATE TABLE fx_quotes (
                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           base_currency TEXT NOT NULL,
                           quote_currency TEXT NOT NULL,
                           rate NUMERIC(18,8) NOT NULL CHECK (rate > 0),
                           status TEXT NOT NULL DEFAULT 'LOCKED', -- LOCKED, EXPIRED
                           created_at TIMESTAMP NOT NULL DEFAULT now(),
                           locked_at TIMESTAMP,
                           expires_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_fx_pair ON fx_quotes(base_currency, quote_currency);

-- Transactions
CREATE TABLE transactions (
                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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

-- Status timeline/audit log
CREATE TABLE transaction_status_history (
                                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                            transaction_id UUID NOT NULL REFERENCES transactions(id),
                                            from_status TEXT NOT NULL,
                                            to_status TEXT NOT NULL,
                                            reason TEXT,
                                            at_time TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_hist_tx ON transaction_status_history(transaction_id, at_time);

-- Ledger accounts (per currency)
CREATE TABLE ledger_accounts (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 code TEXT NOT NULL,
                                 currency TEXT NOT NULL,
                                 type TEXT NOT NULL CHECK (type IN ('ASSET','LIABILITY','REVENUE','EXPENSE')),
                                 CONSTRAINT uq_ledger_code_currency UNIQUE(code, currency)
);

-- Ledger postings (idempotent grouping)
CREATE TABLE ledger_postings (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 transaction_id UUID NOT NULL REFERENCES transactions(id),
                                 posting_type TEXT NOT NULL, -- DEBIT_LOCK, SETTLE, REVERSE
                                 unique_key TEXT NOT NULL UNIQUE,
                                 created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_post_tx ON ledger_postings(transaction_id);

-- Ledger entries (double-entry lines)
CREATE TABLE ledger_entries (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                posting_id UUID NOT NULL REFERENCES ledger_postings(id),
                                transaction_id UUID NOT NULL REFERENCES transactions(id),
                                ledger_account_id UUID NOT NULL REFERENCES ledger_accounts(id),

                                currency TEXT NOT NULL,
                                amount NUMERIC(18,2) NOT NULL CHECK (amount > 0),
                                direction TEXT NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),

                                created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_ledger_tx ON ledger_entries(transaction_id);

-- Webhook raw log (store everything, idempotent by fingerprint+provider_reference)
CREATE TABLE webhook_events (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                provider_reference TEXT,
                                event_fingerprint TEXT NOT NULL,
                                raw_body TEXT NOT NULL,
                                signature TEXT NOT NULL,
                                received_at TIMESTAMP NOT NULL DEFAULT now(),
                                processed_at TIMESTAMP,
                                processing_status TEXT NOT NULL CHECK (processing_status IN ('RECEIVED','PROCESSED','DUPLICATE','ERROR')),
                                error_message TEXT,
                                CONSTRAINT uq_webhook_dedup UNIQUE (provider_reference, event_fingerprint)
);
CREATE INDEX idx_webhook_provider_ref ON webhook_events(provider_reference);

-- Idempotency
CREATE TABLE idempotency_records (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     endpoint TEXT NOT NULL,
                                     idem_key TEXT NOT NULL,
                                     request_hash TEXT NOT NULL,
                                     response_json TEXT NOT NULL,
                                     transaction_id UUID REFERENCES transactions(id),
                                     created_at TIMESTAMP NOT NULL DEFAULT now(),
                                     CONSTRAINT uq_idempo_endpoint_key UNIQUE (endpoint, idem_key)
);

-- Seed: example ledger accounts for NGN + USD (expand as needed)
INSERT INTO ledger_accounts(code, currency, type) VALUES
                                                      ('CUST_AVAILABLE','NGN','LIABILITY'),
                                                      ('CUST_LOCKED','NGN','LIABILITY'),
                                                      ('CUST_AVAILABLE','USD','LIABILITY'),
                                                      ('CUST_LOCKED','USD','LIABILITY'),
                                                      ('PROVIDER_CLEARING','NGN','ASSET'),
                                                      ('PROVIDER_CLEARING','USD','ASSET');