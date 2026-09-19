CREATE TABLE reconciliation_runs (
    id UUID PRIMARY KEY,
    trigger_source VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    stale_threshold_seconds BIGINT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    payments_scanned INTEGER NOT NULL DEFAULT 0,
    mismatches_found INTEGER NOT NULL DEFAULT 0,
    repairs_succeeded INTEGER NOT NULL DEFAULT 0,
    repairs_failed INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(1000)
);

CREATE TABLE reconciliation_mismatches (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES reconciliation_runs (id),
    payment_id UUID NOT NULL REFERENCES payments (id),
    mismatch_type VARCHAR(64) NOT NULL,
    payment_status VARCHAR(32) NOT NULL,
    expected_evidence VARCHAR(500) NOT NULL,
    actual_evidence VARCHAR(1000) NOT NULL,
    repair_action VARCHAR(32) NOT NULL,
    repair_status VARCHAR(16) NOT NULL,
    repair_detail VARCHAR(1000),
    detected_at TIMESTAMPTZ NOT NULL,
    repaired_at TIMESTAMPTZ
);

CREATE INDEX idx_reconciliation_runs_started ON reconciliation_runs (started_at DESC);
CREATE INDEX idx_reconciliation_mismatches_run ON reconciliation_mismatches (run_id, detected_at);
CREATE INDEX idx_reconciliation_mismatches_payment ON reconciliation_mismatches (payment_id, detected_at DESC);
