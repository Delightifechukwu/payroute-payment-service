-- Seed accounts first
INSERT INTO accounts (id, name, created_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'Sender Account', NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO accounts (id, name, created_at)
VALUES ('22222222-2222-2222-2222-222222222222', 'Recipient Account', NOW())
ON CONFLICT (id) DO NOTHING;

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