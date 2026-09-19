CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID,
    channel VARCHAR(32),
    template VARCHAR(64),
    destination VARCHAR(255),
    status VARCHAR(32),
    payload_json VARCHAR(4000),
    last_error VARCHAR(1000),
    attempts INT,
    created_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ
);

CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_status ON notifications (status);
