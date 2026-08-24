#!/usr/bin/env bash
set -euo pipefail

readonly RUN_ID="${SETTLEMENT_CRASH_RUN_ID:-$(date +%Y%m%d%H%M%S)-$$}"
readonly SAFE_RUN_ID="${RUN_ID//-/_}"
readonly TEST_DATABASE="settlement_crash_${SAFE_RUN_ID}"
readonly TEST_TOPIC="settlement.crash-window.${RUN_ID}"
readonly TEST_GROUP="settlement-crash-window-${RUN_ID}"
readonly CRASH_CONTAINER="settlement-crash-${RUN_ID}"
readonly RECOVERY_CONTAINER="settlement-recovery-${RUN_ID}"
readonly DEBUG_PORT="${SETTLEMENT_CRASH_DEBUG_PORT:-15005}"
readonly EVENT_ID="bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
readonly DELIVERY_ID=990002
readonly ORDER_ID=990102
readonly RESTAURANT_ID=990012
readonly SHIPPER_ID=990023
readonly TIMEOUT_SECONDS="${SETTLEMENT_CRASH_TIMEOUT_SECONDS:-120}"
readonly KEEP_TEST_ARTIFACTS="${KEEP_SETTLEMENT_CRASH_ARTIFACTS:-false}"
readonly PROBE_CLASS='com.delivery.settlement_service.listener.DeliveryCompletedEventListener$1'
readonly PROBE_METHOD='afterCommit'
readonly JAR_PATH='settlement-service/target/settlement-service-0.0.1-SNAPSHOT.jar'

if [[ ! "$RUN_ID" =~ ^[a-zA-Z0-9][a-zA-Z0-9-]*$ ]]; then
  printf 'SETTLEMENT_CRASH_RUN_ID contains unsupported characters: %s\n' "$RUN_ID" >&2
  exit 1
fi

for command in docker unzip grep awk; do
  command -v "$command" >/dev/null
done

if [[ -n "${JAVA_HOME:-}" ]]; then
  readonly JAVA_BIN="$JAVA_HOME/bin/java"
else
  readonly JAVA_BIN="$(command -v java)"
fi

if ! docker info >/dev/null 2>&1; then
  printf '%s\n' 'Docker daemon is unavailable; crash-window proof was not executed.' >&2
  exit 1
fi

[[ -f "$JAR_PATH" ]] || {
  printf 'Missing %s; package settlement-service with JDK 17 first.\n' "$JAR_PATH" >&2
  exit 1
}
unzip -l "$JAR_PATH" \
  | grep -F 'DeliveryCompletedEventListener$1.class' >/dev/null || {
    printf '%s\n' 'Settlement JAR does not contain the afterCommit callback class.' >&2
    exit 1
  }

probe_pid=''
probe_log="$(mktemp)"
database_created=false
topic_created=false

cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM

  if [[ -n "$probe_pid" ]]; then
    kill "$probe_pid" >/dev/null 2>&1 || true
  fi
  docker rm -f "$CRASH_CONTAINER" "$RECOVERY_CONTAINER" >/dev/null 2>&1 || true

  if [[ "$KEEP_TEST_ARTIFACTS" != 'true' ]]; then
    if [[ "$topic_created" == 'true' ]]; then
      docker compose exec -T kafka kafka-topics \
        --bootstrap-server kafka:9092 --delete --topic "$TEST_TOPIC" \
        >/dev/null 2>&1 || true
    fi
    if [[ "$database_created" == 'true' ]]; then
      docker compose exec -T postgres dropdb -U postgres --force "$TEST_DATABASE" \
        >/dev/null 2>&1 || true
    fi
  fi
  rm -f "$probe_log"
  exit "$exit_code"
}
trap cleanup EXIT INT TERM

wait_for_container_log() {
  local container="$1"
  local pattern="$2"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    if docker logs "$container" 2>&1 | grep -F "$pattern" >/dev/null; then
      return 0
    fi
    if [[ "$(docker inspect --format '{{.State.Status}}' "$container" 2>/dev/null || true)" \
        != 'running' ]]; then
      docker logs --tail 160 "$container" >&2 || true
      return 1
    fi
    sleep 1
  done
  docker logs --tail 160 "$container" >&2 || true
  return 1
}

wait_for_probe_log() {
  local pattern="$1"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    if grep -F "$pattern" "$probe_log" >/dev/null; then
      return 0
    fi
    if [[ -n "$probe_pid" ]] && ! kill -0 "$probe_pid" >/dev/null 2>&1; then
      cat "$probe_log" >&2
      return 1
    fi
    sleep 1
  done
  cat "$probe_log" >&2
  return 1
}

group_field() {
  local column="$1"
  docker compose exec -T kafka kafka-consumer-groups \
      --bootstrap-server kafka:9092 --group "$TEST_GROUP" --describe 2>/dev/null \
    | awk -v topic="$TEST_TOPIC" -v column="$column" \
        '$2 == topic && $3 == "0" { print $column; exit }'
}

database_invariants_hold() {
  [[ "$(docker compose exec -T postgres psql -U postgres -d "$TEST_DATABASE" -At \
    -c "SELECT ((SELECT count(*) FROM settlement_receipts WHERE event_id = '$EVENT_ID') = 1
            AND (SELECT count(*) FROM transactions WHERE order_id = $ORDER_ID) = 4
            AND (SELECT deposit_balance FROM balances
                 WHERE entity_id = $SHIPPER_ID AND entity_type = 'SHIPPER') = 0
            AND (SELECT total_cod_collected FROM balances
                 WHERE entity_id = $SHIPPER_ID AND entity_type = 'SHIPPER') = 120000)::int;")" == '1' ]]
}

printf '%s\n' '[SETTLEMENT-CRASH] Build current Settlement image'
docker compose build settlement-service >/dev/null

docker compose exec -T postgres createdb -U postgres "$TEST_DATABASE"
database_created=true
docker compose exec -T kafka kafka-topics --bootstrap-server kafka:9092 \
  --create --topic "$TEST_TOPIC" --partitions 1 --replication-factor 1 >/dev/null
topic_created=true

docker compose run -d --no-deps --name "$CRASH_CONTAINER" \
  -p "127.0.0.1:${DEBUG_PORT}:5005" \
  -e 'JAVA_TOOL_OPTIONS=-Xmx384m -Xms256m -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=0.0.0.0:5005' \
  -e "SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/$TEST_DATABASE" \
  -e SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e "SPRING_KAFKA_CONSUMER_GROUP_ID=$TEST_GROUP" \
  -e "SETTLEMENT_DELIVERY_COMPLETED_TOPIC=$TEST_TOPIC" \
  settlement-service >/dev/null
wait_for_container_log "$CRASH_CONTAINER" 'Started SettlementServiceApplication'

docker compose exec -T postgres psql -U postgres -d "$TEST_DATABASE" \
  -v shipper_id="$SHIPPER_ID" -v deposit_amount=120000 \
  < scripts/seed-settlement.sql >/dev/null

"$JAVA_BIN" --add-modules jdk.jdi scripts/JdwpBreakpointProbe.java \
  127.0.0.1 "$DEBUG_PORT" "$PROBE_CLASS" "$PROBE_METHOD" \
  </dev/null >"$probe_log" 2>&1 &
probe_pid=$!
wait_for_probe_log 'BREAKPOINT_'

payload="{\"eventId\":\"$EVENT_ID\",\"eventType\":\"DELIVERY_COMPLETED\",\"deliveryId\":$DELIVERY_ID,\"orderId\":$ORDER_ID,\"restaurantId\":$RESTAURANT_ID,\"shipperId\":$SHIPPER_ID,\"restaurantEarnings\":80000,\"restaurantCommission\":20000,\"shippingFee\":20000,\"shipperEarnings\":17000,\"shippingCommission\":3000,\"totalPlatformEarnings\":23000,\"paymentMethod\":\"COD\"}"
printf '%s:%s\n' "$DELIVERY_ID" "$payload" \
  | docker compose exec -T kafka kafka-console-producer \
      --bootstrap-server kafka:9092 --topic "$TEST_TOPIC" \
      --property parse.key=true --property key.separator=: >/dev/null

wait_for_probe_log 'BREAKPOINT_REACHED'
database_invariants_hold || {
  printf '%s\n' 'Database was not durably committed at the afterCommit breakpoint.' >&2
  exit 1
}

log_end_offset="$(group_field 5)"
current_offset="$(group_field 4)"
[[ "$log_end_offset" == '1' && "$current_offset" != '1' ]] || {
  printf 'Offset advanced before ACK: current=%s end=%s\n' \
    "${current_offset:-<none>}" "${log_end_offset:-<none>}" >&2
  exit 1
}

printf '%s\n' '[SETTLEMENT-CRASH] SIGKILL after DB commit and before ACK'
docker kill --signal=KILL "$CRASH_CONTAINER" >/dev/null
kill "$probe_pid" >/dev/null 2>&1 || true
probe_pid=''
docker rm "$CRASH_CONTAINER" >/dev/null

docker compose run -d --no-deps --name "$RECOVERY_CONTAINER" \
  -e "SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/$TEST_DATABASE" \
  -e SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e "SPRING_KAFKA_CONSUMER_GROUP_ID=$TEST_GROUP" \
  -e "SETTLEMENT_DELIVERY_COMPLETED_TOPIC=$TEST_TOPIC" \
  settlement-service >/dev/null
wait_for_container_log "$RECOVERY_CONTAINER" \
  "[Idempotent] Settlement event $EVENT_ID already applied, skipping"

deadline=$((SECONDS + TIMEOUT_SECONDS))
while (( SECONDS < deadline )); do
  current_offset="$(group_field 4)"
  log_end_offset="$(group_field 5)"
  lag="$(group_field 6)"
  if [[ "$current_offset" == '1' && "$log_end_offset" == '1' && "$lag" == '0' ]]; then
    break
  fi
  sleep 1
done
[[ "$current_offset" == '1' && "$log_end_offset" == '1' && "$lag" == '0' ]]
database_invariants_hold

printf '%s\n' \
  'Settlement crash-window proof passed: durable commit, uncommitted offset, SIGKILL, exact redelivery, unchanged ledger, lag 0.'
