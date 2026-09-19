CREATE TABLE fraud_assessments (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    risk_score DOUBLE PRECISION NOT NULL,
    decision VARCHAR(16) NOT NULL,
    explanation VARCHAR(2000),
    triggered_rules VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_fraud_user ON fraud_assessments (user_id);
CREATE INDEX idx_fraud_decision ON fraud_assessments (decision);
