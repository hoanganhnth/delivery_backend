#!/usr/bin/env bash
set -euo pipefail

# Rehearses two Match replicas across the crash window after Match has
# atomically staged a result but before its outbox relay can publish it. It
# also delivers a real Saga stop before a paused Find is consumed. Everything
# runs in an isolated Compose project with fresh PostgreSQL/Kafka volumes; it
# never attaches to or stops the developer's canonical backend_delivery project.

readonly RUN_ID="${SAGA_MATCH_CRASH_RUN_ID:-$(date +%Y%m%d%H%M%S)-$$}"
readonly PROJECT_NAME="delivery_saga_match_crash_${RUN_ID//-/_}"
readonly POSTGRES_VOLUME="delivery_saga_match_crash_${RUN_ID}_postgres_data"
readonly KAFKA_VOLUME="delivery_saga_match_crash_${RUN_ID}_kafka_data"
readonly TIMEOUT_SECONDS="${SAGA_MATCH_CRASH_TIMEOUT_SECONDS:-240}"
readonly POLL_SECONDS=2
readonly -a COMPOSE_FILES=(
  -f docker-compose.yml
  -f docker-compose.secrets.yml
  -f docker-compose.isolated-e2e.yml
)
readonly COMPOSE_FILE_VALUE="docker-compose.yml:docker-compose.secrets.yml:docker-compose.isolated-e2e.yml"

command -v docker >/dev/null
command -v curl >/dev/null
command -v jq >/dev/null
command -v mvn >/dev/null

if [[ ! "$RUN_ID" =~ ^[a-zA-Z0-9][a-zA-Z0-9_-]*$ ]]; then
  printf 'SAGA_MATCH_CRASH_RUN_ID contains unsupported characters: %s\n' "$RUN_ID" >&2
  exit 2
fi
if [[ "$PROJECT_NAME" == "backend_delivery" ]]; then
  printf '%s\n' 'Crash rehearsal must never use the canonical Compose project.' >&2
  exit 2
fi
if ! docker info >/dev/null 2>&1; then
  printf '%s\n' 'Docker daemon is unavailable; crash rehearsal was not executed.' >&2
  exit 1
fi

compose() {
  COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
  POSTGRES_VOLUME_NAME="$POSTGRES_VOLUME" \
  KAFKA_VOLUME_NAME="$KAFKA_VOLUME" \
    docker compose "${COMPOSE_FILES[@]}" "$@"
}

if docker volume inspect "$POSTGRES_VOLUME" >/dev/null 2>&1 \
    || docker volume inspect "$KAFKA_VOLUME" >/dev/null 2>&1; then
  printf 'Refusing to reuse crash-rehearsal volumes for %s.\n' "$RUN_ID" >&2
  exit 2
fi
if [[ -n "$(compose ps -aq)" ]]; then
  printf 'Refusing to reuse existing crash-rehearsal project %s.\n' "$PROJECT_NAME" >&2
  exit 2
fi

isolated_config="$(compose config --format json)"
printf '%s' "$isolated_config" | jq -e '
  . as $root
  | (["postgres", "redis", "kafka", "api-gateway", "match-service", "saga-orchestrator-service",
      "delivery-service", "notification-service"]
     | all(. as $service | ($root.services[$service].container_name // null) == null))
  and (($root.services["api-gateway"].ports // []) | length) == 1
  and $root.services["api-gateway"].ports[0].target == 8079
  and $root.services["api-gateway"].ports[0].host_ip == "127.0.0.1"
' >/dev/null || {
  printf '%s\n' 'Isolated Compose crash-rehearsal configuration is unsafe.' >&2
  exit 2
}

started=false
seed_result=""
cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM
  [[ -z "$seed_result" ]] || rm -f "$seed_result"
  if [[ "$started" == "true" ]]; then
    if (( exit_code != 0 )); then
      printf '%s\n' 'Crash rehearsal failed; capturing disposable service logs...' >&2
      compose logs --no-color --tail=180 \
        saga-orchestrator-service match-service delivery-service notification-service >&2 || true
    fi
    compose down -v --remove-orphans >/dev/null 2>&1 || true
  fi
  exit "$exit_code"
}
trap cleanup EXIT INT TERM

wait_for() {
  local description="$1"
  shift
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
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
  compose exec -T postgres psql -U postgres -d "$database" -At -c "$query"
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

step 'package service artifacts for the disposable stack'
bash scripts/verify-build-baseline.sh
mvn -q -DskipTests package

step 'start isolated stack with the Match result relay disabled and Find listener paused'
started=true
MATCH_OUTBOX_RELAY_ENABLED=false \
MATCH_CANCELLATION_PROJECTION_RELAY_ENABLED=true \
MATCH_REDIS_HOST=redis \
MATCH_REDIS_TIMEOUT=1000ms \
MATCH_KAFKA_FIND_LISTENER_AUTO_STARTUP=false \
MATCH_KAFKA_STOP_LISTENER_AUTO_STARTUP=true \
COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
POSTGRES_VOLUME_NAME="$POSTGRES_VOLUME" \
KAFKA_VOLUME_NAME="$KAFKA_VOLUME" \
RUNTIME_ISOLATED=true \
RUNTIME_REBUILD_IMAGES=true \
MATCHING_INITIAL_MAX_RETRY_ATTEMPTS=2 \
MATCHING_INITIAL_DELAY_SECONDS=1 \
MATCHING_INITIAL_MAX_DELAY_SECONDS=2 \
MATCHING_INITIAL_BACKOFF_MULTIPLIER=1.0 \
  bash scripts/verify-runtime-startup.sh

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

seed_result="$(mktemp)"
COMPOSE_FILE="$COMPOSE_FILE_VALUE" \
COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
POSTGRES_VOLUME_NAME="$POSTGRES_VOLUME" \
KAFKA_VOLUME_NAME="$KAFKA_VOLUME" \
SEED_OUTPUT_FILE="$seed_result" \
SEED_LOCAL_FIXTURE_EMAIL_VERIFIED=true \
BASE="$BASE" bash scripts/seed.sh >/dev/null

customer_token="$(jq -er '.customerToken' "$seed_result")"
owner_token="$(jq -er '.ownerToken' "$seed_result")"
shipper_token="$(jq -er '.shipperToken' "$seed_result")"
restaurant_id="$(jq -er '.restaurantId' "$seed_result")"
menu_item_id="$(jq -er '.menuItemId' "$seed_result")"

create_and_confirm_cod_order() {
  local order_response created_order_id
  order_response="$(curl --fail-with-body --silent --show-error -X POST "$BASE/api/orders" \
    -H "Authorization: Bearer $customer_token" -H 'Content-Type: application/json' \
    -d "{\"restaurantId\":$restaurant_id,\"deliveryAddress\":\"Crash rehearsal address\",\"deliveryLat\":10.7740,\"deliveryLng\":106.7040,\"customerName\":\"Crash Rehearsal Customer\",\"customerPhone\":\"0900000009\",\"paymentMethod\":\"COD\",\"items\":[{\"menuItemId\":$menu_item_id,\"quantity\":1}]}")"
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
    --bootstrap-server kafka:9092 --describe --group match-service 2>/dev/null || true)"
  awk '$2 == "saga.command.stop-matching" && $3 == "0" && $4 == $5 { found = 1 }
       END { exit found ? 0 : 1 }' <<<"$group_description"
}

stop_dlt_is_empty() {
  if ! compose exec -T kafka kafka-topics --bootstrap-server kafka:9092 \
      --describe --topic saga.command.stop-matching.DLT >/dev/null 2>&1; then
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

step 'create and confirm a COD order until Match stages a PENDING result outbox'
order_response="$(curl --fail-with-body --silent --show-error -X POST "$BASE/api/orders" \
  -H "Authorization: Bearer $customer_token" -H 'Content-Type: application/json' \
  -d "{\"restaurantId\":$restaurant_id,\"deliveryAddress\":\"Crash rehearsal address\",\"deliveryLat\":10.7740,\"deliveryLng\":106.7040,\"customerName\":\"Crash Rehearsal Customer\",\"customerPhone\":\"0900000009\",\"paymentMethod\":\"COD\",\"items\":[{\"menuItemId\":$menu_item_id,\"quantity\":1}]}")"
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
sleep 5

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

compose down -v --remove-orphans
started=false
trap - EXIT INT TERM
rm -f "$seed_result"
