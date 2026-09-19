ALTER TABLE wallets ALTER COLUMN available_balance_minor SET NOT NULL;
ALTER TABLE wallets ALTER COLUMN reserved_balance_minor SET NOT NULL;
ALTER TABLE wallets ADD CONSTRAINT chk_wallet_balances_minor_nonnegative
    CHECK (available_balance_minor >= 0 AND reserved_balance_minor >= 0);
ALTER TABLE ledger_entries ALTER COLUMN amount_minor SET NOT NULL;
ALTER TABLE ledger_entries ALTER COLUMN available_balance_after_minor SET NOT NULL;
ALTER TABLE ledger_entries ALTER COLUMN reserved_balance_after_minor SET NOT NULL;
ALTER TABLE ledger_entries ADD CONSTRAINT chk_ledger_amount_minor_positive CHECK (amount_minor > 0);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY, topic VARCHAR(255) NOT NULL, event_key VARCHAR(255) NOT NULL,
    event_type VARCHAR(500) NOT NULL, payload TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ, attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL, last_error VARCHAR(500)
);
CREATE INDEX idx_wallet_outbox_ready
    ON outbox_events (next_attempt_at, created_at) WHERE published_at IS NULL;
