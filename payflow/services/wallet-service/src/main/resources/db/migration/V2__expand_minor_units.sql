ALTER TABLE wallets ADD COLUMN available_balance_minor BIGINT;
ALTER TABLE wallets ADD COLUMN reserved_balance_minor BIGINT;
UPDATE wallets SET
    available_balance_minor = ROUND(available_balance * 100)::BIGINT,
    reserved_balance_minor = ROUND(reserved_balance * 100)::BIGINT;

ALTER TABLE ledger_entries ADD COLUMN amount_minor BIGINT;
ALTER TABLE ledger_entries ADD COLUMN available_balance_after_minor BIGINT;
ALTER TABLE ledger_entries ADD COLUMN reserved_balance_after_minor BIGINT DEFAULT 0;
UPDATE ledger_entries SET
    amount_minor = ROUND(amount * 100)::BIGINT,
    available_balance_after_minor = ROUND(balance_after * 100)::BIGINT,
    reserved_balance_after_minor = 0;
UPDATE ledger_entries SET entry_type = 'CAPTURE' WHERE entry_type = 'DEBIT';

CREATE FUNCTION sync_wallet_balance_units() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.available_balance_minor := COALESCE(NEW.available_balance_minor, ROUND(NEW.available_balance * 100)::BIGINT);
        NEW.reserved_balance_minor := COALESCE(NEW.reserved_balance_minor, ROUND(NEW.reserved_balance * 100)::BIGINT);
        NEW.available_balance := COALESCE(NEW.available_balance, NEW.available_balance_minor / 100.0);
        NEW.reserved_balance := COALESCE(NEW.reserved_balance, NEW.reserved_balance_minor / 100.0);
    ELSE
        IF NEW.available_balance_minor IS DISTINCT FROM OLD.available_balance_minor THEN
            NEW.available_balance := NEW.available_balance_minor / 100.0;
        ELSIF NEW.available_balance IS DISTINCT FROM OLD.available_balance THEN
            NEW.available_balance_minor := ROUND(NEW.available_balance * 100)::BIGINT;
        END IF;
        IF NEW.reserved_balance_minor IS DISTINCT FROM OLD.reserved_balance_minor THEN
            NEW.reserved_balance := NEW.reserved_balance_minor / 100.0;
        ELSIF NEW.reserved_balance IS DISTINCT FROM OLD.reserved_balance THEN
            NEW.reserved_balance_minor := ROUND(NEW.reserved_balance * 100)::BIGINT;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_sync_wallet_balance_units BEFORE INSERT OR UPDATE ON wallets
    FOR EACH ROW EXECUTE FUNCTION sync_wallet_balance_units();

CREATE FUNCTION sync_ledger_amount_units() RETURNS trigger AS $$
BEGIN
    NEW.amount_minor := COALESCE(NEW.amount_minor, ROUND(NEW.amount * 100)::BIGINT);
    NEW.available_balance_after_minor := COALESCE(NEW.available_balance_after_minor, ROUND(NEW.balance_after * 100)::BIGINT);
    NEW.reserved_balance_after_minor := COALESCE(NEW.reserved_balance_after_minor, 0);
    NEW.amount := COALESCE(NEW.amount, NEW.amount_minor / 100.0);
    NEW.balance_after := COALESCE(NEW.balance_after, NEW.available_balance_after_minor / 100.0);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_sync_ledger_amount_units BEFORE INSERT OR UPDATE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION sync_ledger_amount_units();
