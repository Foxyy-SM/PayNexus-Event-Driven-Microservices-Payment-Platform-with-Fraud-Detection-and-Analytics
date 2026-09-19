ALTER TABLE reported_transactions ADD COLUMN amount_minor BIGINT;
UPDATE reported_transactions SET amount_minor = ROUND(amount * 100)::BIGINT WHERE amount_minor IS NULL;
CREATE FUNCTION sync_transaction_amount_units() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.amount_minor := COALESCE(NEW.amount_minor, ROUND(NEW.amount * 100)::BIGINT);
        NEW.amount := COALESCE(NEW.amount, NEW.amount_minor / 100.0);
    ELSIF NEW.amount_minor IS DISTINCT FROM OLD.amount_minor THEN
        NEW.amount := NEW.amount_minor / 100.0;
    ELSIF NEW.amount IS DISTINCT FROM OLD.amount THEN
        NEW.amount_minor := ROUND(NEW.amount * 100)::BIGINT;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_sync_transaction_amount_units BEFORE INSERT OR UPDATE ON reported_transactions
    FOR EACH ROW EXECUTE FUNCTION sync_transaction_amount_units();
