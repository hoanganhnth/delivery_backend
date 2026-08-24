#!/usr/bin/env bash
set -euo pipefail

readonly POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-delivery-postgres}"
readonly POSTGRES_DATABASE="${POSTGRES_DATABASE:-delivery_db}"
readonly CLEAN_SCHEMA="b8_delivery_clean_rehearsal"
readonly V5_SCHEMA="b8_delivery_v5_rehearsal"
readonly V9_SCHEMA="b8_delivery_v9_rehearsal"
readonly MIGRATION_DIR="delivery-service/src/main/resources/db/migration"

command -v docker >/dev/null

psql_admin() {
  docker exec "$POSTGRES_CONTAINER" psql \
    -U postgres -d "$POSTGRES_DATABASE" -v ON_ERROR_STOP=1 "$@"
}

psql_schema() {
  local schema="$1"
  shift
  docker exec "$POSTGRES_CONTAINER" psql \
    -U postgres -d "$POSTGRES_DATABASE" -v ON_ERROR_STOP=1 -q \
    -c "SET search_path TO $schema" "$@"
}

apply_migration() {
  local schema="$1"
  local migration="$2"
  docker exec -i "$POSTGRES_CONTAINER" psql \
    -U postgres -d "$POSTGRES_DATABASE" -v ON_ERROR_STOP=1 \
    -c "SET search_path TO $schema" -f - < "$MIGRATION_DIR/$migration"
}

apply_recovery() {
  local schema="$1"
  docker exec -i "$POSTGRES_CONTAINER" psql \
    -U postgres -d "$POSTGRES_DATABASE" -v ON_ERROR_STOP=1 \
    -c "SET search_path TO $schema" -f - \
    < docs/migrations/2026-07-23-delivery-create-event-identity.sql
}

cleanup() {
  psql_admin -q -c "DROP SCHEMA IF EXISTS $CLEAN_SCHEMA CASCADE" >/dev/null
  psql_admin -q -c "DROP SCHEMA IF EXISTS $V5_SCHEMA CASCADE" >/dev/null
  psql_admin -q -c "DROP SCHEMA IF EXISTS $V9_SCHEMA CASCADE" >/dev/null
}
trap cleanup EXIT

cleanup
# The documented V8→V9 recovery requires pgcrypto. Install it in the canonical
# public schema before switching search_path so cleanup cannot own/drop it.
psql_admin -q -c "CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public" >/dev/null
psql_admin -q -c "CREATE SCHEMA $CLEAN_SCHEMA" >/dev/null
psql_admin -q -c "CREATE SCHEMA $V5_SCHEMA" >/dev/null
psql_admin -q -c "CREATE SCHEMA $V9_SCHEMA" >/dev/null

# Clean PostgreSQL path: every Delivery migration in Flyway order.
for migration in \
  V1__create_deliveries.sql \
  V3__add_shipping_fee_to_deliveries.sql \
  V4__add_shipper_offer_to_deliveries.sql \
  V5__one_active_delivery_per_shipper.sql \
  V6__create_delivery_outbox.sql \
  V7__add_restaurant_owner_to_deliveries.sql \
  V8__add_delivery_create_event_identity.sql \
  V9__enforce_delivery_create_event_identity.sql \
  V10__drop_legacy_status_constraint_and_add_query_indexes.sql; do
  apply_migration "$CLEAN_SCHEMA" "$migration" >/dev/null
done
psql_schema "$CLEAN_SCHEMA" -At -c \
  "SELECT is_nullable FROM information_schema.columns WHERE table_schema='$CLEAN_SCHEMA' AND table_name='deliveries' AND column_name='create_event_id'" \
  | grep -qx 'NO'

# V5 must reject an already-corrupt upgrade, then enforce the partial unique
# index after an operator resolves the duplicate assignment.
for migration in \
  V1__create_deliveries.sql \
  V3__add_shipping_fee_to_deliveries.sql \
  V4__add_shipper_offer_to_deliveries.sql; do
  apply_migration "$V5_SCHEMA" "$migration" >/dev/null
done
psql_schema "$V5_SCHEMA" -q -c \
  "INSERT INTO deliveries(order_id, shipper_id, status, creator_id) VALUES (5101, 51, 'ASSIGNED', 1), (5102, 51, 'DELIVERING', 1)" >/dev/null
if apply_migration "$V5_SCHEMA" V5__one_active_delivery_per_shipper.sql >/dev/null 2>&1; then
  echo "Delivery V5 unexpectedly accepted duplicate active assignments" >&2
  exit 1
fi
psql_schema "$V5_SCHEMA" -q -c \
  "UPDATE deliveries SET status='CANCELLED' WHERE order_id=5102" >/dev/null
apply_migration "$V5_SCHEMA" V5__one_active_delivery_per_shipper.sql >/dev/null
psql_schema "$V5_SCHEMA" -q -c \
  "INSERT INTO deliveries(order_id, status, creator_id) VALUES (5103, 'PENDING', 1), (5104, 'PENDING', 1)" >/dev/null

# Two acceptance transactions race for the same shipper. The first commits;
# the second must block and then fail on the PostgreSQL partial unique index.
psql_schema "$V5_SCHEMA" -q -c \
  "BEGIN; UPDATE deliveries SET shipper_id=52, status='ASSIGNED' WHERE order_id=5103; SELECT pg_sleep(3); COMMIT" >/dev/null &
v5_first_pid=$!
sleep 1
set +e
psql_schema "$V5_SCHEMA" -q -c \
  "UPDATE deliveries SET shipper_id=52, status='ASSIGNED' WHERE order_id=5104" >/dev/null 2>&1
v5_second_status=$?
wait "$v5_first_pid"
v5_first_status=$?
set -e
if [[ "$v5_first_status" -ne 0 || "$v5_second_status" -eq 0 ]]; then
  echo "Delivery V5 concurrent assignment fence did not serialize as expected" >&2
  exit 1
fi

# V9 must stop on historic rows with unknown event identity. The documented
# recovery assigns markers, after which V9 and V10 can complete.
for migration in \
  V1__create_deliveries.sql \
  V3__add_shipping_fee_to_deliveries.sql \
  V4__add_shipper_offer_to_deliveries.sql \
  V5__one_active_delivery_per_shipper.sql \
  V6__create_delivery_outbox.sql \
  V7__add_restaurant_owner_to_deliveries.sql \
  V8__add_delivery_create_event_identity.sql; do
  apply_migration "$V9_SCHEMA" "$migration" >/dev/null
done
psql_schema "$V9_SCHEMA" -q -c \
  "INSERT INTO deliveries(order_id, status, creator_id) VALUES (5901, 'PENDING', 1)" >/dev/null
if apply_migration "$V9_SCHEMA" V9__enforce_delivery_create_event_identity.sql >/dev/null 2>&1; then
  echo "Delivery V9 unexpectedly accepted an unreconciled historic row" >&2
  exit 1
fi
apply_recovery "$V9_SCHEMA" >/dev/null
apply_migration "$V9_SCHEMA" V9__enforce_delivery_create_event_identity.sql >/dev/null
apply_migration "$V9_SCHEMA" V10__drop_legacy_status_constraint_and_add_query_indexes.sql >/dev/null

# Concurrent create-command inserts with the same event UUID: one commit wins,
# the other must fail after waiting on the V9 unique constraint.
readonly REPLAY_UUID="00000000-0000-0000-0000-000000005901"
psql_schema "$V9_SCHEMA" -q -c \
  "BEGIN; INSERT INTO deliveries(order_id, status, creator_id, create_event_id) VALUES (5902, 'PENDING', 1, '$REPLAY_UUID'); SELECT pg_sleep(3); COMMIT" >/dev/null &
v9_first_pid=$!
sleep 1
set +e
psql_schema "$V9_SCHEMA" -q -c \
  "INSERT INTO deliveries(order_id, status, creator_id, create_event_id) VALUES (5903, 'PENDING', 1, '$REPLAY_UUID')" >/dev/null 2>&1
v9_second_status=$?
wait "$v9_first_pid"
v9_first_status=$?
set -e
if [[ "$v9_first_status" -ne 0 || "$v9_second_status" -eq 0 ]]; then
  echo "Delivery V9 concurrent create-event fence did not serialize as expected" >&2
  exit 1
fi

printf '%s\n' \
  "Delivery PostgreSQL migration proof passed: clean V1-V10, V5 preflight/race, V9 recovery/replay race."
