ALTER TABLE reported_transactions ALTER COLUMN amount_minor SET NOT NULL;
ALTER TABLE reported_transactions ADD CONSTRAINT chk_tx_amount_minor_nonnegative CHECK (amount_minor >= 0);
