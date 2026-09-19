ALTER TABLE fraud_assessments ADD COLUMN amount_minor BIGINT;
UPDATE fraud_assessments SET amount_minor = 0 WHERE amount_minor IS NULL;
