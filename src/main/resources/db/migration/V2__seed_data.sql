-- Seed ledger accounts for all currencies
INSERT INTO ledger_accounts(id, code, currency, type) VALUES
    (gen_random_uuid(), 'CUST_AVAILABLE','NGN','LIABILITY'),
    (gen_random_uuid(), 'CUST_LOCKED','NGN','LIABILITY'),
    (gen_random_uuid(), 'CUST_AVAILABLE','USD','LIABILITY'),
    (gen_random_uuid(), 'CUST_LOCKED','USD','LIABILITY'),
    (gen_random_uuid(), 'CUST_AVAILABLE','EUR','LIABILITY'),
    (gen_random_uuid(), 'CUST_LOCKED','EUR','LIABILITY'),
    (gen_random_uuid(), 'CUST_AVAILABLE','GBP','LIABILITY'),
    (gen_random_uuid(), 'CUST_LOCKED','GBP','LIABILITY'),
    (gen_random_uuid(), 'PROVIDER_CLEARING','NGN','ASSET'),
    (gen_random_uuid(), 'PROVIDER_CLEARING','USD','ASSET'),
    (gen_random_uuid(), 'PROVIDER_CLEARING','EUR','ASSET'),
    (gen_random_uuid(), 'PROVIDER_CLEARING','GBP','ASSET')
ON CONFLICT (code, currency) DO NOTHING;

-- Seed account balances
INSERT INTO account_balances (id, account_id, currency, available, locked)
VALUES ('11111111-1111-1111-1111-111111111110','11111111-1111-1111-1111-111111111111','USD',10000,0)
    ON CONFLICT (account_id, currency) DO NOTHING;

INSERT INTO account_balances (id, account_id, currency, available, locked)
VALUES ('11111111-1111-1111-1111-111111111112','11111111-1111-1111-1111-111111111111','NGN',5000000,0)
    ON CONFLICT (account_id, currency) DO NOTHING;

INSERT INTO account_balances (id, account_id, currency, available, locked)
VALUES ('22222222-2222-2222-2222-222222222220','22222222-2222-2222-2222-222222222222','EUR',1000,0)
    ON CONFLICT (account_id, currency) DO NOTHING;

INSERT INTO account_balances (id, account_id, currency, available, locked)
VALUES ('22222222-2222-2222-2222-222222222221','22222222-2222-2222-2222-222222222222','USD',500,0)
    ON CONFLICT (account_id, currency) DO NOTHING;