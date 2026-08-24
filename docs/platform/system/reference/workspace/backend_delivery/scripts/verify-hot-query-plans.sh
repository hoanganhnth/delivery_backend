#!/usr/bin/env bash
set -euo pipefail

readonly POSTGRES_IMAGE="${POSTGRES_IMAGE:-postgres:16-alpine}"
readonly CONTAINER_NAME="${CONTAINER_NAME:-phase4-hot-query-plans}"
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly OUTPUT_DIR="${OUTPUT_DIR:-$SCRIPT_DIR/../target/phase4-query-plans}"

command -v docker >/dev/null
command -v python3 >/dev/null
if [[ ! "$CONTAINER_NAME" =~ ^phase4-hot-query ]]; then
  printf 'Benchmark container name must start with phase4-hot-query.\n' >&2
  exit 2
fi
if docker ps -a --format '{{.Names}}' | grep -Fxq "$CONTAINER_NAME"; then
  printf 'Refusing to reuse existing container %s.\n' "$CONTAINER_NAME" >&2
  exit 2
fi

work_dir="$(mktemp -d)"
cleanup() {
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
  rm -rf "$work_dir"
}
trap cleanup EXIT
mkdir -p "$OUTPUT_DIR"
rm -f "$OUTPUT_DIR"/*.json "$OUTPUT_DIR"/summary.tsv

docker run -d --name "$CONTAINER_NAME" \
  -e POSTGRES_PASSWORD=phase4-query-only "$POSTGRES_IMAGE" >/dev/null
deadline=$((SECONDS + 60))
until docker exec "$CONTAINER_NAME" pg_isready -U postgres >/dev/null 2>&1; do
  if (( SECONDS >= deadline )); then
    printf 'Benchmark PostgreSQL did not become ready.\n' >&2
    exit 1
  fi
  sleep 1
done

printf '[QUERY-PLAN] load representative dataset\n'
docker exec -i -e PGPASSWORD=phase4-query-only "$CONTAINER_NAME" \
  psql -X -v ON_ERROR_STOP=1 -U postgres -d postgres \
  < "$SCRIPT_DIR/fixtures/phase4-hot-query-baseline.sql" >/dev/null

capture_plan() {
  local phase="$1" name="$2" query="$3"
  docker exec -i -e PGPASSWORD=phase4-query-only "$CONTAINER_NAME" \
    psql -X -v ON_ERROR_STOP=1 -U postgres -d postgres -At -c \
    "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) ${query}" \
    > "$OUTPUT_DIR/${phase}-${name}.json"
}

capture_suite() {
  local phase="$1"
  capture_plan "$phase" orders_customer \
    "SELECT * FROM orders WHERE user_id=1200 ORDER BY created_at DESC LIMIT 50"
  capture_plan "$phase" orders_restaurant \
    "SELECT * FROM orders WHERE restaurant_id=2100 ORDER BY created_at DESC LIMIT 50"
  capture_plan "$phase" orders_shipper \
    "SELECT * FROM orders WHERE shipper_id=3200 ORDER BY created_at DESC LIMIT 50"
  capture_plan "$phase" orders_status \
    "SELECT * FROM orders WHERE status='DELIVERED' ORDER BY created_at DESC LIMIT 50"
  capture_plan "$phase" orders_global \
    "SELECT * FROM orders ORDER BY created_at DESC, id DESC LIMIT 50"
  capture_plan "$phase" delivery_history \
    "SELECT * FROM deliveries WHERE shipper_id=3200 ORDER BY created_at DESC LIMIT 100"
  capture_plan "$phase" delivery_current_offer \
    "SELECT * FROM deliveries WHERE offered_shipper_id=3200 AND status='WAIT_SHIPPER_CONFIRM' AND offer_expires_at > timestamp '2026-08-01' ORDER BY offer_expires_at,id LIMIT 2"
  capture_plan "$phase" settlement_entity \
    "SELECT * FROM transactions WHERE entity_id=3201 AND entity_type='SHIPPER' ORDER BY created_at DESC,id DESC LIMIT 100"
  capture_plan "$phase" settlement_pending \
    "SELECT * FROM transactions WHERE status='PENDING' AND reason='WITHDRAW' ORDER BY created_at DESC,id DESC LIMIT 100"
  capture_plan "$phase" settlement_global \
    "SELECT * FROM transactions ORDER BY created_at DESC,id DESC LIMIT 100"
  capture_plan "$phase" notification_inbox \
    "SELECT * FROM notifications WHERE user_id=1200 AND is_read=false ORDER BY created_at DESC LIMIT 100"
  capture_plan "$phase" shipper_availability \
    "SELECT * FROM shipper WHERE is_online=true LIMIT 100"
  capture_plan "$phase" order_outbox \
    "SELECT * FROM order_outbox_events WHERE status='PENDING' AND next_attempt_at <= timestamp '2026-07-31' ORDER BY next_attempt_at,created_at,id LIMIT 50"
  capture_plan "$phase" delivery_outbox \
    "SELECT * FROM delivery_outbox_events WHERE status='PENDING' AND next_attempt_at <= timestamp '2026-07-31' ORDER BY next_attempt_at,created_at,id LIMIT 50"
}

printf '[QUERY-PLAN] capture baseline plans\n'
capture_suite before

printf '[QUERY-PLAN] apply candidate indexes with bounded lock timeout\n'
index_started="$(date +%s)"
docker exec -i -e PGPASSWORD=phase4-query-only "$CONTAINER_NAME" \
  psql -X -v ON_ERROR_STOP=1 -U postgres -d postgres \
  < "$SCRIPT_DIR/fixtures/phase4-hot-query-indexes.sql" >/dev/null
index_elapsed=$(( $(date +%s) - index_started ))

printf '[QUERY-PLAN] capture optimized plans\n'
capture_suite after

grep -Fq 'Seq Scan' "$OUTPUT_DIR/before-orders_global.json"
grep -Fq 'idx_orders_created_id' "$OUTPUT_DIR/after-orders_global.json"
grep -Eq 'idx_transactions_(entity|entity_status)' "$OUTPUT_DIR/after-settlement_entity.json"
grep -Fq 'idx_transactions_status_reason_created' "$OUTPUT_DIR/after-settlement_pending.json"
grep -Fq 'idx_transactions_created_at' "$OUTPUT_DIR/after-settlement_global.json"
grep -Fq 'idx_order_outbox_pending' "$OUTPUT_DIR/after-order_outbox.json"
grep -Fq 'idx_delivery_outbox_pending' "$OUTPUT_DIR/after-delivery_outbox.json"
grep -Fq 'idx_notifications_user_read_created' "$OUTPUT_DIR/after-notification_inbox.json"
grep -Fq 'idx_shipper_online' "$OUTPUT_DIR/after-shipper_availability.json"

python3 - "$OUTPUT_DIR" <<'PY'
import json
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
names = sorted(p.name.removeprefix("before-").removesuffix(".json")
               for p in root.glob("before-*.json"))
with (root / "summary.tsv").open("w", encoding="utf-8") as output:
    output.write("query\tbefore_ms\tafter_ms\tbefore_root\tafter_root\n")
    for name in names:
        before = json.loads((root / f"before-{name}.json").read_text())[0]
        after = json.loads((root / f"after-{name}.json").read_text())[0]
        output.write(
            f"{name}\t{before['Execution Time']:.3f}\t{after['Execution Time']:.3f}"
            f"\t{before['Plan']['Node Type']}\t{after['Plan']['Node Type']}\n"
        )
PY

printf 'Hot-query plan proof passed on representative data; index build=%ss, evidence=%s\n' \
  "$index_elapsed" "$OUTPUT_DIR"
