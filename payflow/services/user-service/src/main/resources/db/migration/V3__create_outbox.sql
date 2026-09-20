CREATE TABLE outbox_events (
 id UUID PRIMARY KEY, topic VARCHAR(255) NOT NULL, event_key VARCHAR(255) NOT NULL,
 event_type VARCHAR(500) NOT NULL, payload TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL,
 published_at TIMESTAMPTZ, attempts INTEGER NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMPTZ NOT NULL, last_error VARCHAR(500)
);
