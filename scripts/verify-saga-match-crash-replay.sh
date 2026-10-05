#!/usr/bin/env bash
set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

readonly RUN_ID="${SAGA_MATCH_CRASH_RUN_ID:-$(date +%Y%m%d%H%M%S)-$$}"
readonly OWNER="saga-match-crash-$(date +%s)-$$-${RANDOM}"
readonly PROJECT_NAME="$OWNER"
readonly OWNERSHIP_KEY='delivery.saga-match-crash.owner'
readonly OWNERSHIP_LABEL="$OWNERSHIP_KEY=$OWNER"
readonly TIMEOUT_SECONDS="${SAGA_MATCH_CRASH_TIMEOUT_SECONDS:-240}"
readonly POLL_SECONDS=2
if (( $# != 0 )); then
  printf '%s\n' 'No positional arguments are supported; use SAGA_MATCH_CRASH_RUN_ID and SAGA_MATCH_CRASH_TIMEOUT_SECONDS.' >&2
  exit 2
fi
if [[ ! "$RUN_ID" =~ ^[a-zA-Z0-9][a-zA-Z0-9-]{0,39}$ ]]; then
  printf 'SAGA_MATCH_CRASH_RUN_ID contains unsupported characters: %s\n' "$RUN_ID" >&2
  exit 2
fi
if [[ ! "$TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  printf '%s\n' 'SAGA_MATCH_CRASH_TIMEOUT_SECONDS must be a positive integer.' >&2
  exit 2
fi
if (( ${#TIMEOUT_SECONDS} > 5 )) || (( TIMEOUT_SECONDS > 86400 )); then
  printf '%s\n' 'SAGA_MATCH_CRASH_TIMEOUT_SECONDS must not exceed 86400.' >&2
  exit 2
fi
for dependency in python3 docker curl jq; do
  command -v "$dependency" >/dev/null
done
python3 scripts/saga_match_crash_fixture.py jars

bounded_command() {
  local limit="$1"
  shift
  python3 -c '
import os, signal, subprocess, sys
process = subprocess.Popen(sys.argv[2:], start_new_session=True)
try:
    sys.exit(process.wait(timeout=float(sys.argv[1])))
except subprocess.TimeoutExpired:
    os.killpg(process.pid, signal.SIGKILL)
    process.wait()
    sys.exit(124)
' "$limit" "$@"
}

docker() {
  local limit="$TIMEOUT_SECONDS"
  if [[ -n "${OBSERVATION_DEADLINE:-}" ]]; then
    limit=$((OBSERVATION_DEADLINE - SECONDS))
    (( limit > 0 )) || return 124
    (( limit <= 10 )) || limit=10
  fi
  bounded_command "$limit" docker "$@"
}

curl() {
  local limit=10
  if [[ -n "${OBSERVATION_DEADLINE:-}" ]]; then
    limit=$((OBSERVATION_DEADLINE - SECONDS))
    (( limit > 0 )) || return 124
    (( limit <= 10 )) || limit=10
  fi
  bounded_command "$limit" curl --connect-timeout 5 --max-time "$limit" "$@"
}

if ! docker info >/dev/null 2>&1; then
  printf '%s\n' 'Docker daemon is unavailable; crash rehearsal was not executed.' >&2
  exit 1
fi

fixture_dir="$(mktemp -d)"
readonly COMPOSE_FILE_VALUE="$fixture_dir/compose.json"
seed_result="$fixture_dir/seed.json"
started=false
cleanup() {
  local exit_code=$? resource label kind list_flag
  trap - EXIT INT TERM
  unset OBSERVATION_DEADLINE
  if [[ "$started" == "true" ]]; then
    for kind in container volume network image; do
      list_flag=-q
      [[ "$kind" != container ]] || list_flag=-aq
      while IFS= read -r resource; do
        [[ -n "$resource" ]] || continue
        if [[ "$kind" == container || "$kind" == image ]]; then
          label="$(bounded_command 10 docker "$kind" inspect --format "{{index .Config.Labels \"$OWNERSHIP_KEY\"}}" "$resource" 2>/dev/null || true)"
        else
          label="$(bounded_command 10 docker "$kind" inspect --format "{{index .Labels \"$OWNERSHIP_KEY\"}}" "$resource" 2>/dev/null || true)"
        fi
        [[ "$label" == "$OWNER" ]] || continue
        if [[ "$kind" == container ]]; then
          if (( exit_code != 0 )); then
            bounded_command 10 docker logs --tail=180 "$resource" >&2 || true
          fi
          bounded_command 10 docker container rm -fv "$resource" >/dev/null 2>&1 || true
        else
          bounded_command 10 docker "$kind" rm "$resource" >/dev/null 2>&1 || true
        fi
      done <<< "$(bounded_command 10 docker "$kind" ls "$list_flag" --filter "label=$OWNERSHIP_LABEL" 2>/dev/null || true)"
    done
  fi
  rm -rf "$fixture_dir"
  exit "$exit_code"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

compose() {
  python3 scripts/saga_match_crash_fixture.py match "$COMPOSE_FILE_VALUE"
  docker compose --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE_VALUE" "$@"
}

wait_for() {
  local description="$1"
  shift
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  local OBSERVATION_DEADLINE="$deadline"
  while (( SECONDS < deadline )); do
    if "$@"; then
      return 0
    fi
    sleep "$POLL_SECONDS"
  done
  printf 'Timed out waiting for %s.\n' "$description" >&2
  return 1
}

psql_value() {
  local database="$1"
  local query="$2"
  compose exec -T postgres psql -U postgres -d "$database" -v ON_ERROR_STOP=1 -At -c "$query"
}

wait_for_service_healthy() {
  local service="$1"
  local expected_replicas="${2:-1}"
  local container_id state health
  local -a container_ids=()
  while IFS= read -r container_id; do
    [[ -z "$container_id" ]] || container_ids+=("$container_id")
  done <<< "$(compose ps -aq "$service")"
  [[ "${#container_ids[@]}" -eq "$expected_replicas" ]] || return 1
  for container_id in "${container_ids[@]}"; do
    state="$(docker inspect --format '{{.State.Status}}' "$container_id")"
    health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container_id")"
    [[ "$state" == "running" && ( "$health" == "healthy" || "$health" == "none" ) ]] || return 1
  done
}

step() {
  printf '[SAGA-MATCH-CRASH] %s\n' "$1"
}

step 'construct uniquely owned fixture from read-only Compose configuration'
docker compose -f docker-compose.yml -f docker-compose.secrets.yml config --format json \
  | python3 scripts/saga_match_crash_fixture.py fixture "$OWNER" > "$COMPOSE_FILE_VALUE"

step 'start isolated stack with the Match result relay disabled and Find listener paused'
started=true
compose build
compose up -d tracing-collector config-server discovery-server postgres redis kafka elasticsearch
for service in config-server discovery-server postgres redis kafka elasticsearch; do
  wait_for "$service readiness" wait_for_service_healthy "$service"
done
compose up -d --no-deps auth-service
wait_for 'Auth readiness before JWKS resource services' wait_for_service_healthy auth-service
MATCH_OUTBOX_RELAY_ENABLED=false \
MATCH_CANCELLATION_PROJECTION_RELAY_ENABLED=true \
MATCH_REDIS_HOST=redis \
MATCH_REDIS_TIMEOUT=1000ms \
MATCH_KAFKA_FIND_LISTENER_AUTO_STARTUP=false \
MATCH_KAFKA_STOP_LISTENER_AUTO_STARTUP=true \
  compose up -d --no-deps --scale match-service=2
while IFS= read -r service; do
  replicas=1
  [[ "$service" != match-service ]] || replicas=2
  wait_for "$service readiness" wait_for_service_healthy "$service" "$replicas"
done <<< "$(compose config --services)"

step 'scale Match to two replicas with Find paused and Stop active'
MATCH_OUTBOX_RELAY_ENABLED=false \
MATCH_CANCELLATION_PROJECTION_RELAY_ENABLED=true \
MATCH_REDIS_HOST=redis \
MATCH_REDIS_TIMEOUT=1000ms \
MATCH_KAFKA_FIND_LISTENER_AUTO_STARTUP=false \
MATCH_KAFKA_STOP_LISTENER_AUTO_STARTUP=true \
  compose up -d --no-deps --force-recreate --scale match-service=2 match-service >/dev/null
wait_for 'two Match replicas with Find paused to become healthy' \
  wait_for_service_healthy match-service 2

gateway_mapping="$(compose port api-gateway 8079 | head -n 1)"
gateway_port="${gateway_mapping##*:}"
if [[ ! "$gateway_port" =~ ^[0-9]+$ ]]; then
  printf 'Could not resolve isolated Gateway port from %s.\n' "$gateway_mapping" >&2
  exit 1
fi
BASE="http://127.0.0.1:${gateway_port}"

# Seed derives shipper idCard/phone from RUN_ID; CreateShipperRequest caps idCard
# at 20 chars, so the seed id must stay short (ID-<seed id>-<n> <= 20).
seed_run_id="m$(date +%s)"
bounded_command "$TIMEOUT_SECONDS" env \
COMPOSE_FILE="$COMPOSE_FILE_VALUE" \
COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
RUN_ID="$seed_run_id" \
SEED_OUTPUT_FILE="$seed_result" \
SEED_LOCAL_FIXTURE_EMAIL_VERIFIED=true \
BASE="$BASE" bash scripts/seed.sh > "$fixture_dir/seed.log" 2>&1 || {
  status=$?
  printf 'Seed failed (exit %s); last seed output:\n' "$status" >&2
  tail -n 40 "$fixture_dir/seed.log" >&2
  exit "$status"
}

customer_token="$(jq -er '.customerToken' "$seed_result")"
owner_token="$(jq -er '.ownerToken' "$seed_result")"
shipper_token="$(jq -er '.shipperToken' "$seed_result")"
restaurant_id="$(jq -er '.restaurantId' "$seed_result")"
menu_item_id="$(jq -er '.menuItemId' "$seed_result")"

create_and_confirm_cod_order() {
  local order_response created_order_id
  order_response="$(curl --fail-with-body --silent --show-error -X POST "$BASE/api/orders" \
    -H "Authorization: Bearer $customer_token" -H 'Content-Type: application/json' \
    -d "{\"restaurantId\":$restaurant_id,\"deliveryAddress\":\"Crash rehearsal address\",\"deliveryLat\":20.9760,\"deliveryLng\":105.7750,\"customerName\":\"Crash Rehearsal Customer\",\"customerPhone\":\"0900000009\",\"paymentMethod\":\"COD\",\"items\":[{\"menuItemId\":$menu_item_id,\"quantity\":1}]}")"
  created_order_id="$(jq -er '.data.id // .id' <<<"$order_response")"
  [[ "$created_order_id" =~ ^[0-9]+$ ]] || {
    printf '%s\n' 'Order response lacked a numeric id.' >&2
    return 1
  }

  curl --fail-with-body --silent --show-error -X POST \
    "$BASE/api/restaurants/orders/$created_order_id/confirm" \
    -H "Authorization: Bearer $owner_token" -H 'Content-Type: application/json' \
    -d "{\"restaurantId\":$restaurant_id,\"estimatedPrepTime\":15}" >/dev/null
  printf '%s\n' "$created_order_id"
}

cancel_stopped_order() {
  curl --fail-with-body --silent --show-error -X PUT \
    "$BASE/api/orders/$stopped_order_id/cancel" \
    -H "Authorization: Bearer $customer_token" -H 'Content-Type: application/json' \
    -d '{"reason":"Cross-topic stop-before-find rehearsal"}' >/dev/null
}

step 'emit a real Saga stop before its paused Find command is consumed'
stopped_order_id="$(create_and_confirm_cod_order)"

saga_find_is_sent_for_stopped_order() {
  [[ "$(psql_value saga_db "SELECT count(*) FROM saga_outbox_events
      WHERE aggregate_id = '$stopped_order_id'
        AND topic = 'saga.command.find-shipper'
        AND status = 'SENT';")" == '1' ]]
}

wait_for 'Saga Find command while Match Find listener is paused' \
  saga_find_is_sent_for_stopped_order

stopped_find_payload="$(psql_value saga_db "SELECT payload FROM saga_outbox_events
    WHERE aggregate_id = '$stopped_order_id'
      AND topic = 'saga.command.find-shipper'
    ORDER BY created_at DESC LIMIT 1;")"
stopped_delivery_id="$(jq -er '.deliveryId' <<<"$stopped_find_payload")"
stopped_session_id="$(jq -er '.matchingSessionId' <<<"$stopped_find_payload")"
[[ "$stopped_delivery_id" =~ ^[0-9]+$ && "$stopped_session_id" =~ ^[0-9a-fA-F-]{36}$ ]] || {
  printf '%s\n' 'Saga Find command lacked a delivery or matching-session identity.' >&2
  exit 1
}
[[ "$(psql_value match_db "SELECT count(*) FROM match_commands
    WHERE order_id = $stopped_order_id;")" == '0' ]] || {
  printf '%s\n' 'Match consumed Find while its Find listener was paused.' >&2
  exit 1
}

stopped_order_cancellation_snapshot() {
  psql_value order_db "SELECT
      status || '|' || COALESCE(cancel_reason, '') || '|' || COALESCE(cancelled_by::text, '')
        || '|' || (SELECT count(*) FROM outbox_events
                    WHERE aggregate_id = '$stopped_order_id' AND topic = 'order.cancelled')
    FROM orders WHERE id = $stopped_order_id;"
}

step 'verify approved Gateway fail-closed behavior during a global Redis outage'
stopped_order_before_global_redis_outage="$(stopped_order_cancellation_snapshot)"
[[ -n "$stopped_order_before_global_redis_outage" ]] || {
  printf 'Could not read the order cancellation state before the Gateway Redis outage.\n' >&2
  exit 1
}
compose stop redis >/dev/null
global_redis_cancel_status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
  --connect-timeout 5 --max-time 5 -X PUT "$BASE/api/orders/$stopped_order_id/cancel" \
  -H "Authorization: Bearer $customer_token" -H 'Content-Type: application/json' \
  -d '{"reason":"Gateway Redis outage policy rehearsal"}')"
[[ "$global_redis_cancel_status" == '503' ]] || {
  printf 'Gateway accepted or timed out a mutation while Redis was down: HTTP %s.\n' \
    "$global_redis_cancel_status" >&2
  exit 1
}
[[ "$(stopped_order_cancellation_snapshot)" == "$stopped_order_before_global_redis_outage" ]] || {
  printf '%s\n' 'Gateway returned Redis-outage 503 but still mutated the Order cancellation state.' >&2
  exit 1
}
compose start redis >/dev/null
wait_for 'Redis to become healthy after the approved Gateway outage policy' \
  wait_for_service_healthy redis

step 'make Redis unavailable only to Match while it consumes Stop'
MATCH_OUTBOX_RELAY_ENABLED=false \
MATCH_CANCELLATION_PROJECTION_RELAY_ENABLED=true \
MATCH_REDIS_HOST=127.0.0.1 \
MATCH_REDIS_TIMEOUT=1000ms \
MATCH_KAFKA_FIND_LISTENER_AUTO_STARTUP=false \
MATCH_KAFKA_STOP_LISTENER_AUTO_STARTUP=true \
  compose up -d --no-deps --force-recreate --scale match-service=2 match-service >/dev/null

wait_for 'customer cancellation after Gateway Redis recovery' cancel_stopped_order

saga_stop_is_sent_for_stopped_order() {
  [[ "$(psql_value saga_db "SELECT count(*) FROM saga_outbox_events
      WHERE aggregate_id = '$stopped_order_id'
        AND topic = 'saga.command.stop-matching'
        AND status = 'SENT';")" == '1' ]]
}

stop_tombstone_exists() {
  [[ "$(psql_value match_db "SELECT count(*) FROM match_cancellation_tombstones
      WHERE delivery_id = $stopped_delivery_id
        AND matching_session_id = '$stopped_session_id';")" == '1' ]]
}

stop_tombstone_projection_is_pending() {
  [[ "$(psql_value match_db "SELECT count(*) FROM match_cancellation_tombstones
      WHERE delivery_id = $stopped_delivery_id
        AND matching_session_id = '$stopped_session_id'
        AND projection_status = 'PENDING'
        AND projection_attempts >= 1;")" == '1' ]]
}

stop_source_offset_is_committed() {
  local group_description
  group_description="$(compose exec -T kafka kafka-consumer-groups \
    --bootstrap-server kafka:9092 --describe --group match-service --timeout 5000 2>/dev/null || true)"
  awk '$2 == "saga.command.stop-matching" && $3 == "0" && $4 ~ /^[0-9]+$/ && $5 > 0 && $4 == $5 { found = 1 }
       END { exit found ? 0 : 1 }' <<<"$group_description"
}

stop_dlt_is_empty() {
  local topics
  topics="$(compose exec -T kafka kafka-topics --bootstrap-server kafka:9092 --list)" || return 1
  if ! grep -Fx 'saga.command.stop-matching.DLT' <<< "$topics" >/dev/null; then
    return 0
  fi
  compose exec -T kafka kafka-run-class kafka.tools.GetOffsetShell \
    --broker-list kafka:9092 --topic saga.command.stop-matching.DLT --time -1 \
    | awk -F: '{ total += $NF } END { exit total == 0 ? 0 : 1 }'
}

stop_tombstone_projection_is_recovered() {
  [[ "$(psql_value match_db "SELECT count(*) FROM match_cancellation_tombstones
      WHERE delivery_id = $stopped_delivery_id
        AND matching_session_id = '$stopped_session_id'
        AND projection_status = 'PROJECTED'
        AND redis_projected_at IS NOT NULL;")" == '1' ]] \
    &&
  [[ "$(compose exec -T redis redis-cli EXISTS \
      "match:cancelled:$stopped_delivery_id:$stopped_session_id")" == '1' ]]
}

wait_for 'Saga stop-matching command to relay' saga_stop_is_sent_for_stopped_order
wait_for 'Match durable cancellation tombstone before Find' stop_tombstone_exists
wait_for 'durable pending Redis cancellation projection after the outage' \
  stop_tombstone_projection_is_pending
wait_for 'Match to commit the Stop source offset after durable fencing' \
  stop_source_offset_is_committed
if ! stop_dlt_is_empty; then
  printf '%s\n' 'Stop-matching reached its DLT despite the durable cancellation fence.' >&2
  exit 1
fi

step 'restore Match Redis connectivity and require durable projection recovery'
MATCH_OUTBOX_RELAY_ENABLED=false \
MATCH_CANCELLATION_PROJECTION_RELAY_ENABLED=true \
MATCH_REDIS_HOST=redis \
MATCH_REDIS_TIMEOUT=1000ms \
MATCH_KAFKA_FIND_LISTENER_AUTO_STARTUP=false \
MATCH_KAFKA_STOP_LISTENER_AUTO_STARTUP=true \
  compose up -d --no-deps --force-recreate --scale match-service=2 match-service >/dev/null
wait_for 'two Match replicas with Redis restored to become healthy' \
  wait_for_service_healthy match-service 2
wait_for 'durable cancellation projection to converge after Redis recovery' \
  stop_tombstone_projection_is_recovered

step 'resume Match Find listener and prove the delayed Find cannot resurrect an offer'
MATCH_OUTBOX_RELAY_ENABLED=false \
MATCH_CANCELLATION_PROJECTION_RELAY_ENABLED=true \
MATCH_REDIS_HOST=redis \
MATCH_REDIS_TIMEOUT=1000ms \
MATCH_KAFKA_FIND_LISTENER_AUTO_STARTUP=true \
MATCH_KAFKA_STOP_LISTENER_AUTO_STARTUP=true \
  compose up -d --no-deps --force-recreate --scale match-service=2 match-service >/dev/null
wait_for 'two Match replicas with Find listener resumed to become healthy' \
  wait_for_service_healthy match-service 2

stopped_match_is_cancelled_without_result() {
  [[ "$(psql_value match_db "SELECT
      (SELECT count(*) FROM match_commands WHERE order_id = $stopped_order_id),
      COALESCE((SELECT status FROM match_commands WHERE order_id = $stopped_order_id
                ORDER BY created_at DESC LIMIT 1), ''),
      (SELECT count(*) FROM match_outbox_events WHERE aggregate_id = '$stopped_order_id');")" \
      == '1|CANCELLED|0' ]]
}

wait_for 'delayed Find to persist as a cancelled Match command' \
  stopped_match_is_cancelled_without_result

stopped_order_offer_exists() {
  local response offer_order
  response="$(curl --silent --show-error "$BASE/api/deliveries/offers/current" \
    -H "Authorization: Bearer $shipper_token" || true)"
  offer_order="$(jq -r '.data.orderId // empty' <<<"$response" 2>/dev/null || true)"
  [[ "$offer_order" == "$stopped_order_id" ]]
}

if stopped_order_offer_exists; then
  printf 'Cancelled generation unexpectedly created a shipper offer for order %s.\n' \
    "$stopped_order_id" >&2
  exit 1
fi
[[ "$(psql_value notification_service_db "SELECT count(*) FROM notifications
    WHERE related_entity_id = $stopped_order_id AND type = 'MATCH_FOUND';")" == '0' ]] || {
  printf '%s\n' 'Cancelled generation unexpectedly notified MATCH_FOUND.' >&2
  exit 1
}
[[ "$(psql_value saga_db "SELECT count(*) FROM saga_outbox_events
    WHERE aggregate_id = '$stopped_order_id'
      AND topic = 'saga.command.cache-shipper-found';")" == '0' ]] || {
  printf '%s\n' 'Cancelled generation unexpectedly emitted a Saga cache command.' >&2
  exit 1
}

step 'republish the shipper location lost with the non-persistent fixture Redis'
# The global Redis outage above restarts a Redis without persistence, so the
# Match GEO projection is empty until the shipper app reports again. Seed puts
# shipper 1 at 20.9730,105.7790 next to the restaurant.
curl --fail-with-body --silent --show-error -X PATCH "$BASE/api/shippers/online-status?isOnline=true" \
  -H "Authorization: Bearer $shipper_token" >/dev/null
curl --fail-with-body --silent --show-error -X POST "$BASE/api/tracking/shipper-locations/update" \
  -H "Authorization: Bearer $shipper_token" -H 'Content-Type: application/json' \
  -d '{"latitude":20.9730,"longitude":105.7790,"isOnline":true}' >/dev/null

match_geo_has_shipper() {
  local count
  count="$(compose exec -T redis sh -c \
    "redis-cli --scan --pattern 'match:shippers:geo*' | head -n 1 | xargs -r redis-cli zcard" 2>/dev/null)"
  [[ "$count" =~ ^[0-9]+$ && "$count" -gt 0 ]]
}
wait_for 'shipper location projected into Match GEO' match_geo_has_shipper

step 'create and confirm a COD order until Match stages a PENDING result outbox'
order_response="$(curl --fail-with-body --silent --show-error -X POST "$BASE/api/orders" \
  -H "Authorization: Bearer $customer_token" -H 'Content-Type: application/json' \
  -d "{\"restaurantId\":$restaurant_id,\"deliveryAddress\":\"Crash rehearsal address\",\"deliveryLat\":20.9760,\"deliveryLng\":105.7750,\"customerName\":\"Crash Rehearsal Customer\",\"customerPhone\":\"0900000009\",\"paymentMethod\":\"COD\",\"items\":[{\"menuItemId\":$menu_item_id,\"quantity\":1}]}")"
order_id="$(jq -er '.data.id // .id' <<<"$order_response")"
[[ "$order_id" =~ ^[0-9]+$ ]] || { printf '%s\n' 'Order response lacked a numeric id.' >&2; exit 1; }

curl --fail-with-body --silent --show-error -X POST \
  "$BASE/api/restaurants/orders/$order_id/confirm" \
  -H "Authorization: Bearer $owner_token" -H 'Content-Type: application/json' \
  -d "{\"restaurantId\":$restaurant_id,\"estimatedPrepTime\":15}" >/dev/null

match_snapshot() {
  psql_value match_db "SELECT
    (SELECT count(*) FROM match_commands WHERE order_id = $order_id),
    COALESCE((SELECT status FROM match_commands WHERE order_id = $order_id ORDER BY created_at DESC LIMIT 1), ''),
    (SELECT count(*) FROM match_outbox_events WHERE aggregate_id = '$order_id'),
    COALESCE((SELECT status FROM match_outbox_events WHERE aggregate_id = '$order_id' ORDER BY id DESC LIMIT 1), '');"
}

match_result_is_pending() {
  [[ "$(match_snapshot)" == '1|RESULT_STAGED|1|PENDING' ]]
}

wait_for 'Match command and unsent result outbox' match_result_is_pending

command_event_id="$(psql_value match_db "SELECT event_id FROM match_commands WHERE order_id = $order_id;")"
delivery_id="$(psql_value match_db "SELECT delivery_id FROM match_commands WHERE order_id = $order_id;")"
find_payload="$(psql_value match_db "SELECT payload FROM match_commands WHERE order_id = $order_id;")"
[[ "$command_event_id" =~ ^[0-9a-fA-F-]{36}$ && "$delivery_id" =~ ^[0-9]+$ && -n "$find_payload" ]] || {
  printf '%s\n' 'Match durable command identity/payload was incomplete.' >&2
  exit 1
}

step 'kill Match after durable staging and recreate it with the relay enabled'
compose kill -s KILL match-service >/dev/null
MATCH_OUTBOX_RELAY_ENABLED=true \
MATCH_CANCELLATION_PROJECTION_RELAY_ENABLED=true \
MATCH_REDIS_HOST=redis \
MATCH_REDIS_TIMEOUT=1000ms \
MATCH_KAFKA_FIND_LISTENER_AUTO_STARTUP=true \
MATCH_KAFKA_STOP_LISTENER_AUTO_STARTUP=true \
  compose up -d --no-deps --force-recreate --scale match-service=2 match-service >/dev/null
wait_for 'two recreated Match replicas to become healthy' wait_for_service_healthy match-service 2

offer_matches_order() {
  local response offer_order offer_status offer_delivery
  response="$(curl --silent --show-error "$BASE/api/deliveries/offers/current" \
    -H "Authorization: Bearer $shipper_token" || true)"
  offer_order="$(jq -r '.data.orderId // empty' <<<"$response" 2>/dev/null || true)"
  offer_status="$(jq -r '.data.status // empty' <<<"$response" 2>/dev/null || true)"
  offer_delivery="$(jq -r '.data.deliveryId // empty' <<<"$response" 2>/dev/null || true)"
  [[ "$offer_order" == "$order_id" && "$offer_status" == "WAIT_SHIPPER_CONFIRM" \
      && "$offer_delivery" == "$delivery_id" ]]
}

wait_for 'durable Match outbox relay to restore the shipper offer' offer_matches_order

match_outbox_is_sent() {
  [[ "$(psql_value match_db "SELECT status FROM match_outbox_events WHERE command_event_id = '$command_event_id';")" == SENT ]]
}

wait_for 'Match outbox row to become SENT' match_outbox_is_sent

step 'replay the original find command after the Match restart'
printf '%s:%s\n' "$delivery_id" "$find_payload" | compose exec -T kafka \
  kafka-console-producer --bootstrap-server kafka:9092 --topic saga.command.find-shipper \
  --property parse.key=true --property key.separator=: >/dev/null
find_source_offset_is_committed() {
  local description
  description="$(compose exec -T kafka kafka-consumer-groups --bootstrap-server kafka:9092 \
    --describe --group match-service --timeout 5000 2>/dev/null)" || return 1
  awk '$2 == "saga.command.find-shipper" {
         found = 1
         if ($4 !~ /^[0-9]+$/ || $5 !~ /^[0-9]+$/ || $4 != $5 || $6 != 0) bad = 1
       } END { exit found && !bad ? 0 : 1 }' <<< "$description"
}
wait_for 'replayed Find source offsets to converge' find_source_offset_is_committed

durable_effects_have_converged() {
  [[ "$(psql_value delivery_db "SELECT count(*) FROM deliveries WHERE order_id = $order_id AND status = 'WAIT_SHIPPER_CONFIRM';")" == '1' ]] \
    && [[ "$(psql_value notification_service_db "SELECT count(*) FROM notifications WHERE related_entity_id = $order_id AND type = 'MATCH_FOUND';")" == '1' ]] \
    && [[ "$(psql_value saga_db "SELECT count(*) FROM saga_outbox_events WHERE aggregate_id = '$order_id' AND topic = 'saga.command.cache-shipper-found';")" == '1' ]]
}
wait_for 'one durable offer, notification and Saga cache command' durable_effects_have_converged

match_snapshot_after="$(match_snapshot)"
delivery_count="$(psql_value delivery_db "SELECT count(*) FROM deliveries WHERE order_id = $order_id;")"
delivery_status="$(psql_value delivery_db "SELECT status FROM deliveries WHERE order_id = $order_id;")"
notification_count="$(psql_value notification_service_db "SELECT count(*) FROM notifications WHERE related_entity_id = $order_id AND type = 'MATCH_FOUND';")"
saga_cache_commands="$(psql_value saga_db "SELECT count(*) FROM saga_outbox_events WHERE aggregate_id = '$order_id' AND topic = 'saga.command.cache-shipper-found';")"

[[ "$match_snapshot_after" == '1|RESULT_STAGED|1|SENT' ]] || {
  printf 'Match command/outbox changed after restart/replay: %s\n' "$match_snapshot_after" >&2
  exit 1
}
[[ "$delivery_count" == '1' && "$delivery_status" == 'WAIT_SHIPPER_CONFIRM' ]] || {
  printf 'Delivery did not converge to one durable offer: count=%s status=%s\n' \
    "$delivery_count" "$delivery_status" >&2
  exit 1
}
[[ "$notification_count" == '1' ]] || {
  printf 'Expected one durable MATCH_FOUND notification, found %s.\n' "$notification_count" >&2
  exit 1
}
[[ "$saga_cache_commands" == '1' ]] || {
  printf 'Expected one Saga cache-shipper command, found %s.\n' "$saga_cache_commands" >&2
  exit 1
}

printf 'Saga/Match two-replica crash/replay rehearsal passed: order=%s delivery=%s command=%s, one Match result/outbox, one Delivery offer, one notification and one Saga cache command.\n' \
  "$order_id" "$delivery_id" "$command_event_id"
