#!/usr/bin/env bash
set -euo pipefail

# Read-only projection check. It deliberately runs against the canonical
# Compose database and never repairs balances automatically.
command -v docker >/dev/null

readonly mismatches="$(docker compose exec -T postgres psql -U postgres -d settlement_db -At <<'SQL'
WITH expected AS (
    SELECT
        entity_id,
        entity_type,
        COALESCE(SUM(CASE
            WHEN wallet_type = 'DEPOSIT' AND status = 'COMPLETED'
                 AND direction = 'CREDIT' THEN amount
            WHEN wallet_type = 'DEPOSIT' AND status = 'COMPLETED'
                 AND direction = 'DEBIT' THEN -amount
            ELSE 0 END), 0) AS deposit_balance,
        COALESCE(SUM(CASE
            WHEN wallet_type = 'EARNINGS' AND status = 'COMPLETED'
                 AND direction = 'CREDIT' THEN amount
            WHEN wallet_type = 'EARNINGS' AND status = 'COMPLETED'
                 AND direction = 'DEBIT' THEN -amount
            WHEN wallet_type = 'EARNINGS' AND status = 'PENDING'
                 AND reason = 'WITHDRAW' AND direction = 'DEBIT' THEN -amount
            ELSE 0 END), 0) AS available_balance,
        COALESCE(SUM(CASE
            WHEN wallet_type = 'EARNINGS' AND status = 'PENDING'
                 AND reason = 'WITHDRAW' AND direction = 'DEBIT' THEN amount
            ELSE 0 END), 0) AS pending_balance,
        COALESCE(SUM(CASE
            WHEN wallet_type = 'EARNINGS' AND status = 'COMPLETED'
                 AND reason = 'HOLD' AND direction = 'DEBIT' THEN amount
            WHEN wallet_type = 'EARNINGS' AND status = 'COMPLETED'
                 AND reason = 'RELEASE' AND direction = 'CREDIT' THEN -amount
            ELSE 0 END), 0) AS holding_balance,
        COALESCE(SUM(CASE
            WHEN wallet_type = 'DEPOSIT' AND status = 'COMPLETED'
                 AND direction = 'CREDIT' THEN amount
            ELSE 0 END), 0) AS total_deposited,
        COALESCE(SUM(CASE
            WHEN reason = 'COD_SETTLEMENT' AND status = 'COMPLETED'
                 AND direction = 'DEBIT' THEN amount
            ELSE 0 END), 0) AS total_cod_collected
    FROM transactions
    GROUP BY entity_id, entity_type
), checks AS (
    SELECT
        COALESCE(b.entity_id, e.entity_id) AS entity_id,
        COALESCE(b.entity_type, e.entity_type) AS entity_type,
        b.deposit_balance,
        COALESCE(e.deposit_balance, 0) AS expected_deposit_balance,
        b.available_balance,
        COALESCE(e.available_balance, 0) AS expected_available_balance,
        b.pending_balance,
        COALESCE(e.pending_balance, 0) AS expected_pending_balance,
        b.holding_balance,
        COALESCE(e.holding_balance, 0) AS expected_holding_balance,
        b.total_deposited,
        COALESCE(e.total_deposited, 0) AS expected_total_deposited,
        b.total_cod_collected,
        COALESCE(e.total_cod_collected, 0) AS expected_total_cod_collected
    FROM balances b
    FULL OUTER JOIN expected e
      ON e.entity_id = b.entity_id AND e.entity_type = b.entity_type
)
SELECT * FROM checks
WHERE deposit_balance IS NULL
   OR expected_deposit_balance <> deposit_balance
   OR expected_available_balance <> available_balance
   OR expected_pending_balance <> pending_balance
   OR expected_holding_balance <> holding_balance
   OR expected_total_deposited <> total_deposited
   OR expected_total_cod_collected <> total_cod_collected;
SQL
)"

if [[ -n "$mismatches" ]]; then
    printf 'Settlement projection mismatches detected:\n%s\n' "$mismatches" >&2
    exit 1
fi

printf '%s\n' 'Settlement reconciliation passed: balances match the transaction ledger.'
