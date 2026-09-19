CREATE TABLE reported_transactions (
    payment_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency CHAR(3),
    merchant_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_tx_user_created ON reported_transactions (user_id, created_at DESC);
CREATE INDEX idx_tx_status_created ON reported_transactions (status, created_at DESC);
CREATE INDEX idx_tx_created ON reported_transactions (created_at);
