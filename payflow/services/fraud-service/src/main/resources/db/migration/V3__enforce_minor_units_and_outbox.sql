ALTER TABLE fraud_assessments ALTER COLUMN amount_minor SET NOT NULL;
ALTER TABLE fraud_assessments ADD CONSTRAINT chk_fraud_amount_minor_nonnegative CHECK (amount_minor >= 0);
CREATE TABLE outbox_events (
 id UUID PRIMARY KEY, topic VARCHAR(255) NOT NULL, event_key VARCHAR(255) NOT NULL,
 event_type VARCHAR(500) NOT NULL, payload TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL,
 published_at TIMESTAMPTZ, attempts INTEGER NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMPTZ NOT NULL, last_error VARCHAR(500)
);
CREATE INDEX idx_fraud_outbox_ready ON outbox_events(next_attempt_at,created_at) WHERE published_at IS NULL;
