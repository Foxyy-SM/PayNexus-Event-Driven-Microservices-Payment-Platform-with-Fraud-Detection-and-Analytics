CREATE INDEX idx_user_outbox_ready ON outbox_events(next_attempt_at,created_at) WHERE published_at IS NULL;
