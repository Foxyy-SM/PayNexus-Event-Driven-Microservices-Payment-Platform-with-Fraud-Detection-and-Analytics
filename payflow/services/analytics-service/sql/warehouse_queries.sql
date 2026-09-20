-- Portable reference transformations for PostgreSQL/Snowflake-style warehouses.
-- Monetary aggregation is always partitioned by currency.

-- Daily payment outcomes.
SELECT
    CAST(COALESCE(initiated_at, updated_at) AS DATE) AS day,
    currency,
    COUNT(*) AS initiated_count,
    SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_count,
    SUM(CASE WHEN status = 'PENDING_REVIEW' THEN 1 ELSE 0 END) AS review_count,
    SUM(CASE WHEN status IN ('FAILED', 'REJECTED') THEN 1 ELSE 0 END) AS failed_count,
    SUM(amount_minor) AS total_volume_minor,
    SUM(CASE WHEN status = 'COMPLETED' THEN amount_minor ELSE 0 END) AS completed_volume_minor
FROM stg_payments
GROUP BY CAST(COALESCE(initiated_at, updated_at) AS DATE), currency;

-- Completed payments without exact append-only capture evidence.
WITH captures AS (
    SELECT payment_id, SUM(amount_minor) AS captured_minor
    FROM stg_wallet_ledger
    WHERE entry_type = 'CAPTURE'
    GROUP BY payment_id
)
SELECT
    p.payment_id,
    p.currency,
    p.amount_minor AS expected_capture_minor,
    COALESCE(c.captured_minor, 0) AS actual_capture_minor
FROM stg_payments p
LEFT JOIN captures c ON c.payment_id = p.payment_id
WHERE p.status = 'COMPLETED'
  AND COALESCE(c.captured_minor, 0) <> p.amount_minor;

-- Ten-minute user velocity, useful for validating the online fraud engine.
SELECT
    user_id,
    payment_id,
    initiated_at,
    COUNT(*) OVER (
        PARTITION BY user_id
        ORDER BY initiated_at
        RANGE BETWEEN INTERVAL '10 minutes' PRECEDING AND CURRENT ROW
    ) AS payments_in_trailing_10m
FROM stg_payments
WHERE initiated_at IS NOT NULL;
