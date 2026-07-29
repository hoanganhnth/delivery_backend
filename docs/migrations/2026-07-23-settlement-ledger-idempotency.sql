-- Recovery/reference equivalent of Flyway Java migration
-- db.migration.V1__settlement_ledger_idempotency.
-- The application migration is authoritative. Run this manually only when
-- Flyway cannot be used, after taking a backup. It aborts rather than deleting,
-- merging, or inventing identity for financial records.
BEGIN;

CREATE TABLE IF NOT EXISTS settlement_receipts (
    event_id uuid PRIMARY KEY,
    order_id bigint NOT NULL,
    delivery_id bigint NOT NULL,
    payload_fingerprint varchar(64) NOT NULL,
    created_at timestamp NOT NULL,
    CONSTRAINT uk_settlement_receipts_order UNIQUE (order_id)
);

DO $$
DECLARE
    duplicate_entries text;
    legacy_settlements text;
BEGIN
    SELECT string_agg(entry_key, E'\n' ORDER BY entry_key)
    INTO duplicate_entries
    FROM (
        SELECT concat_ws(':', order_id, entity_id, entity_type, reason, wallet_type, direction) AS entry_key
        FROM transactions
        WHERE order_id IS NOT NULL
        GROUP BY order_id, entity_id, entity_type, reason, wallet_type, direction
        HAVING count(*) > 1
    ) duplicates;

    IF duplicate_entries IS NOT NULL THEN
        RAISE EXCEPTION 'Duplicate settlement ledger entries require manual reconciliation:%',
            E'\n' || duplicate_entries;
    END IF;

    SELECT string_agg(order_id::text, ', ' ORDER BY order_id)
    INTO legacy_settlements
    FROM transactions transaction_entry
    WHERE transaction_entry.order_id IS NOT NULL
      AND transaction_entry.entity_id = 0
      AND transaction_entry.entity_type = 'SYSTEM'
      AND transaction_entry.reason = 'PLATFORM_COMMISSION'
      AND NOT EXISTS (
          SELECT 1
          FROM settlement_receipts receipt
          WHERE receipt.order_id = transaction_entry.order_id
      );

    IF legacy_settlements IS NOT NULL THEN
        RAISE EXCEPTION 'Existing settled orders need event-id reconciliation before receipt migration: %',
            legacy_settlements;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'transactions'::regclass
          AND conname = 'uk_transactions_order_ledger_entry'
    ) THEN
        ALTER TABLE transactions
            ADD CONSTRAINT uk_transactions_order_ledger_entry
            UNIQUE (order_id, entity_id, entity_type, reason, wallet_type, direction);
    END IF;
END $$;

COMMIT;
