#!/usr/bin/env bash
set -euo pipefail

# Disposable broker proof for the operator manifest. It deliberately uses a
# broker with auto-creation disabled and creates the source topics with three
# partitions, then verifies that every retry/DLT target is created with the
# source partition count and reconciled retention.

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly KAFKA_IMAGE="${KAFKA_IMAGE:-confluentinc/cp-kafka:7.4.0}"
readonly CONTAINER_NAME="${CONTAINER_NAME:-match-kafka-topic-rehearsal}"
readonly EXPECTED_RETENTION_MS="${EXPECTED_RETENTION_MS:-1209600000}"
readonly EXPECTED_PARTITIONS="${EXPECTED_PARTITIONS:-3}"
readonly VERIFY_RUNS="${VERIFY_RUNS:-2}"

command -v docker >/dev/null
if [[ ! "$CONTAINER_NAME" =~ ^match-kafka-topic-rehearsal ]]; then
  printf 'Refusing an unscoped container name: %s\n' "$CONTAINER_NAME" >&2
  exit 2
fi
if docker ps -a --format '{{.Names}}' | grep -Fxq "$CONTAINER_NAME"; then
  printf 'Refusing to reuse existing container %s. Remove it explicitly first.\n' \
    "$CONTAINER_NAME" >&2
  exit 2
fi
if [[ ! "$VERIFY_RUNS" =~ ^[1-9][0-9]*$ ]]; then
  printf 'VERIFY_RUNS must be a positive integer.\n' >&2
  exit 2
fi

readonly SOURCE_TOPICS=(
  order.created
  restaurant.order-confirmed
  restaurant.order-rejected
  delivery.status-updated
  delivery.shipper-offered
  delivery.offer-persisted
  delivery.offer-retired
  delivery.completed
  delivery.exception.reported
  saga.command.update-order-status
  saga.command.create-delivery
  saga.command.cancel-delivery
  saga.command.cache-shipper-found
  saga.command.expire-shipper-offer
  saga.command.mark-shipper-not-found
  payment.completed
  payment.failed
  order.cancelled
  delivery.created.result
  delivery.shipper-accepted
  delivery.shipper-rejected
  delivery.batch.accepted
  delivery.batch.released
  delivery.batch.completed
  shipper.found
  shipper.not-found
  delivery.created.failed
  delivery.cancel.failed
  saga.command.find-shipper
  # Read-only Match algorithm explanations observed by simulator-service. It
  # has no business retry/DLT consumer, but the source must exist explicitly
  # because broker auto-creation is disabled.
  matching.decision-trace
  order.refund-eligible
  saga.command.stop-matching
  shipper.location-updated
  shipper.status-change
  identity.profile.created
  identity.status.changed
  shipper.identity.upserted
)

readonly STANDARD_RETRY_SOURCES=(
  delivery.completed
  saga.command.create-delivery
  saga.command.cancel-delivery
  saga.command.cache-shipper-found
  saga.command.expire-shipper-offer
  saga.command.mark-shipper-not-found
)

readonly SAGA_RETRY_SOURCES=(
  order.created
  order.cancelled
  restaurant.order-confirmed
  delivery.created.result
  delivery.shipper-accepted
  delivery.status-updated
  delivery.shipper-rejected
  shipper.found
  shipper.not-found
  delivery.created.failed
  delivery.cancel.failed
  delivery.offer-persisted
  delivery.offer-retired
)

readonly ORDER_RETRY_SOURCES=(
  restaurant.order-confirmed
  restaurant.order-rejected
  saga.command.update-order-status
)

readonly NOTIFICATION_RETRY_SOURCES=(
  order.created
  delivery.status-updated
  delivery.shipper-offered
)

readonly PROMOTION_RETRY_SOURCES=(
  order.created
  order.cancelled
  order.refund-eligible
)

readonly FLASHSALE_RETRY_SOURCES=(
  order.created
  order.cancelled
  order.refund-eligible
)
readonly INVENTORY_RETRY_SOURCES=(
  order.created
  order.cancelled
  order.refund-eligible
)

readonly TRACKING_RETRY_SOURCES=(
  shipper.location-updated
)

# Tracking's delivery-room listener uses blocking retries and an owner DLT.
# Keep Match's separate source.DLT in DLT_ONLY_SOURCES below.
readonly TRACKING_DLT_ONLY_SOURCES=(
  shipper.status-change
)

readonly AUTH_IDENTITY_RETRY_SOURCES=(identity.profile.created)
readonly USER_IDENTITY_RETRY_SOURCES=(identity.status.changed)
readonly SHIPPER_IDENTITY_RETRY_SOURCES=(identity.status.changed)
readonly DELIVERY_SHIPPER_IDENTITY_RETRY_SOURCES=(shipper.identity.upserted)
readonly TRACKING_SHIPPER_IDENTITY_RETRY_SOURCES=(shipper.identity.upserted)

readonly LEGACY_SHARED_RETRY_SOURCES=(
  order.created
  restaurant.order-confirmed
  restaurant.order-rejected
  delivery.status-updated
  delivery.shipper-offered
  saga.command.update-order-status
  order.cancelled
  delivery.created.result
  delivery.shipper-accepted
  delivery.shipper-rejected
  shipper.found
  shipper.not-found
  delivery.created.failed
  delivery.cancel.failed
)

readonly DLT_ONLY_SOURCES=(
  order.refund-eligible
  saga.command.stop-matching
  shipper.location-updated
  shipper.status-change
  delivery.exception.reported
  delivery.batch.accepted
  delivery.batch.released
  delivery.batch.completed
)

readonly ORDER_PAYMENT_DLT_SOURCES=(
  payment.completed
  payment.failed
)

work_dir="$(mktemp -d)"
cleanup() {
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
  rm -rf "$work_dir"
}
trap cleanup EXIT

printf '[REHEARSAL] start disposable Kafka broker\n'
docker run -d --name "$CONTAINER_NAME" \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  -e KAFKA_AUTO_CREATE_TOPICS_ENABLE=false \
  -e CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qk \
  "$KAFKA_IMAGE" >/dev/null

deadline=$((SECONDS + 90))
until docker exec "$CONTAINER_NAME" \
    kafka-broker-api-versions --bootstrap-server localhost:9092 >/dev/null 2>&1; do
  if (( SECONDS >= deadline )); then
    printf 'Disposable Kafka did not become ready.\n' >&2
    exit 1
  fi
  sleep 2
done

printf '[REHEARSAL] create canonical source topics with %s partitions\n' "$EXPECTED_PARTITIONS"
for topic in "${SOURCE_TOPICS[@]}"; do
  docker exec "$CONTAINER_NAME" kafka-topics \
    --bootstrap-server localhost:9092 \
    --create --if-not-exists --topic "$topic" \
    --partitions "$EXPECTED_PARTITIONS" --replication-factor 1 \
    --config retention.ms=86400000 >/dev/null
done

# Simulate the races the provisioner must repair: a blocking DLT and an
# owner-isolated Saga DLT were auto-created with one partition and stale
# retention before the operator manifest ran.
docker exec "$CONTAINER_NAME" kafka-topics \
  --bootstrap-server localhost:9092 \
  --create --if-not-exists --topic saga.command.stop-matching.DLT \
  --partitions 1 --replication-factor 1 --config retention.ms=1000 >/dev/null
docker exec "$CONTAINER_NAME" kafka-topics \
  --bootstrap-server localhost:9092 \
  --create --if-not-exists --topic order.created.saga.DLT \
  --partitions 1 --replication-factor 1 --config retention.ms=1000 >/dev/null

docker cp "$SCRIPT_DIR/provision-kafka-resilience-topics.sh" \
  "$CONTAINER_NAME:/tmp/provision-kafka-resilience-topics.sh"

printf '[REHEARSAL] provision manifest %s time(s)\n' "$VERIFY_RUNS"
for ((run = 1; run <= VERIFY_RUNS; run++)); do
  docker exec \
    -e KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
    -e KAFKA_TOPICS_BIN=kafka-topics \
    -e KAFKA_CONFIGS_BIN=kafka-configs \
    -e RESILIENCE_REPLICATION_FACTOR=1 \
    -e RESILIENCE_RETENTION_MS="$EXPECTED_RETENTION_MS" \
    -e PROVISION_ORDER_PAYMENT_DLTS=true \
    "$CONTAINER_NAME" bash /tmp/provision-kafka-resilience-topics.sh \
    >"$work_dir/run-$run.log"
done

read_broker_command() {
  local label="$1"
  shift
  local attempt
  local output

  # The disposable broker has just reconciled a large manifest. Kafka's
  # AdminClient can briefly return a node-assignment timeout while metadata is
  # catching up. Retry reads only; an invalid topic/config still fails closed
  # after the bounded attempts below.
  for ((attempt = 1; attempt <= 3; attempt++)); do
    if output="$("$@" 2>&1)"; then
      printf '%s\n' "$output"
      return 0
    fi
    if (( attempt < 3 )); then
      printf '[REHEARSAL] transient broker read failure for %s (attempt %s/3); retrying.\n' \
        "$label" "$attempt" >&2
      sleep 2
    fi
  done

  printf 'Unable to read %s after 3 attempts:\n%s\n' "$label" "$output" >&2
  return 1
}

assert_topic() {
  local topic="$1"
  local description
  local configs

  description="$(read_broker_command "metadata for $topic" \
    docker exec "$CONTAINER_NAME" kafka-topics \
    --bootstrap-server localhost:9092 --describe --topic "$topic")"
  if ! printf '%s\n' "$description" \
      | grep -Eq "PartitionCount:[[:space:]]+$EXPECTED_PARTITIONS([[:space:]]|$)"; then
    printf 'Topic %s did not have expected partition count %s:\n%s\n' \
      "$topic" "$EXPECTED_PARTITIONS" "$description" >&2
    return 1
  fi
  configs="$(read_broker_command "config for $topic" \
    docker exec "$CONTAINER_NAME" kafka-configs \
    --bootstrap-server localhost:9092 --describe \
    --entity-type topics --entity-name "$topic")"
  if ! printf '%s\n' "$configs" | grep -Fq "retention.ms=$EXPECTED_RETENTION_MS"; then
    printf 'Topic %s did not have expected retention.ms=%s:\n%s\n' \
      "$topic" "$EXPECTED_RETENTION_MS" "$configs" >&2
    return 1
  fi
  if ! printf '%s\n' "$configs" | grep -Fq 'cleanup.policy=delete'; then
    printf 'Topic %s did not have cleanup.policy=delete:\n%s\n' "$topic" "$configs" >&2
    return 1
  fi
}

printf '[REHEARSAL] verify every generated retry/DLT target\n'
for source in "${STANDARD_RETRY_SOURCES[@]}"; do
  for delay in 1000 2000 4000; do
    assert_topic "${source}-retry-${delay}"
  done
  assert_topic "${source}.DLT"
done
for source in "${SAGA_RETRY_SOURCES[@]}"; do
  for delay in 1000 2000 4000; do
    assert_topic "${source}-retry-saga-${delay}"
  done
  assert_topic "${source}.saga.DLT"
done
for source in "${ORDER_RETRY_SOURCES[@]}"; do
  for delay in 1000 2000 4000; do
    assert_topic "${source}-retry-order-${delay}"
  done
  assert_topic "${source}.order.DLT"
done
for source in "${NOTIFICATION_RETRY_SOURCES[@]}"; do
  for delay in 1000 2000 4000; do
    assert_topic "${source}-retry-notification-${delay}"
  done
  assert_topic "${source}.notification.DLT"
done
for source in "${PROMOTION_RETRY_SOURCES[@]}"; do
  for delay in 1000 2000 4000; do
    assert_topic "${source}-retry-promotion-${delay}"
  done
  assert_topic "${source}.promotion.DLT"
done
for source in "${FLASHSALE_RETRY_SOURCES[@]}"; do
  for delay in 1000 2000 4000; do
    assert_topic "${source}-retry-flashsale-${delay}"
  done
  assert_topic "${source}.flashsale.DLT"
done
if [[ "${VERIFY_INVENTORY_RETRY_TOPICS:-false}" == "true" ]]; then
  for source in "${INVENTORY_RETRY_SOURCES[@]}"; do
    for delay in 1000 2000 4000; do
      assert_topic "${source}-retry-inventory-${delay}"
    done
    assert_topic "${source}.inventory.DLT"
  done
fi
for source in "${TRACKING_RETRY_SOURCES[@]}"; do
  for delay in 1000 2000 4000; do
    assert_topic "${source}-retry-tracking-${delay}"
  done
  assert_topic "${source}.tracking.DLT"
done
for source in "${TRACKING_DLT_ONLY_SOURCES[@]}"; do
  assert_topic "${source}.tracking.DLT"
done
for source in "${AUTH_IDENTITY_RETRY_SOURCES[@]}"; do
  for delay in 1000 2000 4000; do assert_topic "${source}-retry-auth-identity-${delay}"; done
  assert_topic "${source}.auth-identity.DLT"
done
for source in "${USER_IDENTITY_RETRY_SOURCES[@]}"; do
  for delay in 1000 2000 4000; do assert_topic "${source}-retry-user-identity-${delay}"; done
  assert_topic "${source}.user-identity.DLT"
done
for source in "${SHIPPER_IDENTITY_RETRY_SOURCES[@]}"; do
  for delay in 1000 2000 4000; do assert_topic "${source}-retry-shipper-identity-${delay}"; done
  assert_topic "${source}.shipper-identity.DLT"
done
for source in "${DELIVERY_SHIPPER_IDENTITY_RETRY_SOURCES[@]}"; do
  for delay in 1000 2000 4000; do assert_topic "${source}-retry-delivery-shipper-identity-${delay}"; done
  assert_topic "${source}.delivery-shipper-identity.DLT"
done
for source in "${TRACKING_SHIPPER_IDENTITY_RETRY_SOURCES[@]}"; do
  for delay in 1000 2000 4000; do assert_topic "${source}-retry-tracking-shipper-identity-${delay}"; done
  assert_topic "${source}.tracking-shipper-identity.DLT"
done
for source in "${LEGACY_SHARED_RETRY_SOURCES[@]}"; do
  for delay in 1000 2000 4000; do
    assert_topic "${source}-retry-${delay}"
  done
  assert_topic "${source}.DLT"
done
for delay in 1000 2000 4000; do
  assert_topic "saga.command.find-shipper.retry-${delay}"
done
assert_topic saga.command.find-shipper.DLT
for source in "${DLT_ONLY_SOURCES[@]}"; do
  assert_topic "${source}.DLT"
done
for source in "${ORDER_PAYMENT_DLT_SOURCES[@]}"; do
  assert_topic "${source}.order.DLT"
  assert_topic "${source}.DLT"
done

printf '[REHEARSAL] prove shared source retry records stay in owner-isolated topics\n'
readonly SAGA_RETRY_PROBE='retry-isolation-saga'
readonly NOTIFICATION_RETRY_PROBE='retry-isolation-notification'
printf '%s\n' "$SAGA_RETRY_PROBE" | docker exec -i "$CONTAINER_NAME" \
  kafka-console-producer --bootstrap-server localhost:9092 \
  --topic order.created-retry-saga-1000 >/dev/null
printf '%s\n' "$NOTIFICATION_RETRY_PROBE" | docker exec -i "$CONTAINER_NAME" \
  kafka-console-producer --bootstrap-server localhost:9092 \
  --topic order.created-retry-notification-1000 >/dev/null
saga_retry_observed="$(docker exec "$CONTAINER_NAME" \
  kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic order.created-retry-saga-1000 \
  --group retry-isolation-saga-consumer --from-beginning \
  --max-messages 1 --timeout-ms 10000 2>/dev/null)"
notification_retry_observed="$(docker exec "$CONTAINER_NAME" \
  kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic order.created-retry-notification-1000 \
  --group retry-isolation-notification-consumer --from-beginning \
  --max-messages 1 --timeout-ms 10000 2>/dev/null)"
if [[ "$saga_retry_observed" != "$SAGA_RETRY_PROBE" \
    || "$notification_retry_observed" != "$NOTIFICATION_RETRY_PROBE" ]]; then
  printf 'Owner-isolated retry topic probe crossed streams or did not deliver its expected record.\n' >&2
  exit 1
fi

printf '[REHEARSAL] prove an insufficient existing replication factor fails closed\n'
if replica_failure_output="$(docker exec \
    -e KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
    -e KAFKA_TOPICS_BIN=kafka-topics \
    -e KAFKA_CONFIGS_BIN=kafka-configs \
    -e RESILIENCE_REPLICATION_FACTOR=2 \
    -e RESILIENCE_RETENTION_MS="$EXPECTED_RETENTION_MS" \
    -e PROVISION_ORDER_PAYMENT_DLTS=true \
    "$CONTAINER_NAME" bash /tmp/provision-kafka-resilience-topics.sh 2>&1)"; then
  printf 'Provisioner unexpectedly accepted a target below the requested replication factor.\n' >&2
  exit 1
fi
printf '%s\n' "$replica_failure_output" | grep -Fq 'below required 2'

printf 'Kafka resilience topic rehearsal passed: source-matched partitions, owner-isolated and legacy-drain retry/DLT names, retention reconciliation, Match .retry names, idempotent rerun, and low-replication fail-closed behavior are verified.\n'
