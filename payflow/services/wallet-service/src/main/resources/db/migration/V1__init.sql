CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    available_balance NUMERIC(19, 4) NOT NULL,
    reserved_balance NUMERIC(19, 4) NOT NULL,
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL REFERENCES wallets (id),
    payment_id UUID,
    entry_type VARCHAR(16) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    balance_after NUMERIC(19, 4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_wallets_user ON wallets (user_id);
CREATE INDEX idx_ledger_wallet_created ON ledger_entries (wallet_id, created_at DESC);
CREATE UNIQUE INDEX idx_ledger_payment_debit ON ledger_entries (payment_id, entry_type)
    WHERE payment_id IS NOT NULL;
