#!/usr/bin/env bash
set -euo pipefail

: "${DATABASE_PREFIX+x}"
readonly DATABASE_PREFIX="${DATABASE_PREFIX:-}"
readonly PGHOST="${PGHOST:-localhost}"
readonly PGPORT="${PGPORT:-5432}"
readonly PGUSER="${PGUSER:-postgres}"
readonly PG_CONTAINER="${PG_CONTAINER:-}"
readonly OUTPUT_FILE="${OUTPUT_FILE:-}"

if [[ -n "$DATABASE_PREFIX" && ! "$DATABASE_PREFIX" =~ ^phase4_restore_[a-z0-9_]+_$ ]]; then
  printf 'DATABASE_PREFIX must be empty or an isolated phase4_restore_..._ prefix.\n' >&2
  exit 2
fi

run_psql() {
  local database="$1" query="$2"
  if [[ -n "$PG_CONTAINER" ]]; then
    docker exec -i -e "PGPASSWORD=${PGPASSWORD:-}" "$PG_CONTAINER" \
      psql -X -v ON_ERROR_STOP=1 -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" \
      -d "${DATABASE_PREFIX}${database}" -At -F '|' -c "$query"
  else
    PGPASSWORD="${PGPASSWORD:-}" psql -X -v ON_ERROR_STOP=1 \
      -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" \
      -d "${DATABASE_PREFIX}${database}" -At -F '|' -c "$query"
  fi
}

state_file="$(mktemp)"
trap 'rm -f "$state_file"' EXIT

capture() {
  local label="$1" database="$2" query="$3" rows hash
  rows="$(run_psql "$database" "$query")"
  hash="$(printf '%s' "$rows" | shasum -a 256 | awk '{print $1}')"
  printf '%s|%s|%s\n' "$label" "$(printf '%s\n' "$rows" | sed '/^$/d' | wc -l | tr -d ' ')" "$hash" \
    >> "$state_file"
}

capture orders order_db \
  'SELECT id,user_id,restaurant_id,COALESCE(shipper_id,0),status,total_price,created_at FROM orders ORDER BY id'
capture order_outbox order_db \
  'SELECT event_id,event_type,aggregate_id,status,created_at FROM outbox_events ORDER BY event_id'
capture deliveries delivery_db \
  'SELECT id,order_id,COALESCE(shipper_id,0),status,total_price,created_at FROM deliveries ORDER BY id'
capture delivery_outbox delivery_db \
  'SELECT event_id,event_type,aggregate_id,status,created_at FROM outbox_events ORDER BY event_id'
capture settlement_receipts settlement_db \
  'SELECT event_id,order_id,delivery_id,payload_fingerprint,created_at FROM settlement_receipts ORDER BY event_id'
capture settlement_ledger settlement_db \
  'SELECT id,entity_id,entity_type,order_id,direction,reason,amount,status,wallet_type,created_at FROM transactions ORDER BY id'
capture notification_projection notification_service_db \
  'SELECT id,user_id,type,status,is_read,related_entity_id,related_entity_type,deduplication_key,created_at FROM notifications ORDER BY id'

duplicate_receipts="$(run_psql settlement_db \
  'SELECT count(*) FROM (SELECT event_id FROM settlement_receipts GROUP BY event_id HAVING count(*) > 1) duplicate')"
duplicate_ledger="$(run_psql settlement_db \
  'SELECT count(*) FROM (SELECT order_id,entity_id,entity_type,reason,wallet_type,direction FROM transactions GROUP BY 1,2,3,4,5,6 HAVING count(*) > 1) duplicate')"
duplicate_order_outbox="$(run_psql order_db \
  'SELECT count(*) FROM (SELECT event_id FROM outbox_events GROUP BY event_id HAVING count(*) > 1) duplicate')"
duplicate_delivery_outbox="$(run_psql delivery_db \
  'SELECT count(*) FROM (SELECT event_id FROM outbox_events GROUP BY event_id HAVING count(*) > 1) duplicate')"

if [[ "$duplicate_receipts" != "0" || "$duplicate_ledger" != "0" \
   || "$duplicate_order_outbox" != "0" || "$duplicate_delivery_outbox" != "0" ]]; then
  printf 'Critical uniqueness reconciliation failed.\n' >&2
  exit 1
fi

if [[ -n "$OUTPUT_FILE" ]]; then
  cp "$state_file" "$OUTPUT_FILE"
else
  cat "$state_file"
fi
