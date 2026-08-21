#!/usr/bin/env bash
set -euo pipefail

# Production operator helper. Run from a Kafka admin toolbox/container that has
# kafka-topics.sh and Kafka ACL authority; it deliberately does not rely on
# broker auto-topic-creation. Source topics must already be provisioned with
# their canonical partition counts before this script is used.

: "${KAFKA_BOOTSTRAP_SERVERS:?Set KAFKA_BOOTSTRAP_SERVERS (for example kafka-1:9092)}"

KAFKA_TOPICS_BIN="${KAFKA_TOPICS_BIN:-kafka-topics.sh}"
KAFKA_CONFIGS_BIN="${KAFKA_CONFIGS_BIN:-kafka-configs.sh}"
RESILIENCE_RETENTION_MS="${RESILIENCE_RETENTION_MS:-${DLT_RETENTION_MS:-1209600000}}" # 14 days
RESILIENCE_REPLICATION_FACTOR="${RESILIENCE_REPLICATION_FACTOR:-${DLT_REPLICATION_FACTOR:-3}}"

# These values are the authority used by the standard @RetryableTopic
# annotations in the active services. They remain overrideable for a planned
# topology migration, but the operator must pass the same values to every
# service before publishing new records.
RETRY_ATTEMPTS="${RETRY_ATTEMPTS:-${KAFKA_RETRY_ATTEMPTS:-4}}"
RETRY_INITIAL_DELAY_MS="${RETRY_INITIAL_DELAY_MS:-${KAFKA_RETRY_INITIAL_DELAY_MS:-1000}}"
RETRY_MULTIPLIER="${RETRY_MULTIPLIER:-${KAFKA_RETRY_MULTIPLIER:-2.0}}"
RETRY_MAX_DELAY_MS="${RETRY_MAX_DELAY_MS:-${KAFKA_RETRY_MAX_DELAY_MS:-10000}}"

# Match intentionally uses retryTopicSuffix=".retry" and a fixed four-attempt
# policy, so its names are distinct from the standard "-retry" topics.
MATCH_RETRY_DELAYS_MS="${MATCH_RETRY_DELAYS_MS:-1000,2000,4000}"

# 2026-08 shared-source migration: new deployments route each service owner to
# an isolated retry/DLT topology. Keep the generic legacy targets provisioned
# while old replicas drain them; set this to false only after the documented
# drain gate has succeeded for every old consumer group.
PROVISION_LEGACY_SHARED_RETRY_TOPICS="${PROVISION_LEGACY_SHARED_RETRY_TOPICS:-true}"

# Payment event processing is disabled in the COD-first default, but if it is
# enabled the Order common error handler writes owner-specific DLTs for these
# sources. Do not require absent optional source topics in the normal manifest.
PROVISION_ORDER_PAYMENT_DLTS="${PROVISION_ORDER_PAYMENT_DLTS:-false}"
PROVISION_ANALYTICS_RETRY_TOPICS="${PROVISION_ANALYTICS_RETRY_TOPICS:-false}"
# Identity consumers are deployed dormant in R0 but their retry/DLT topology is
# a precondition for R1/R2/R4. Keep this explicit: do not let a runtime flag
# first enable depend on broker auto-topic creation.
PROVISION_IDENTITY_RETRY_TOPICS="${PROVISION_IDENTITY_RETRY_TOPICS:-true}"

if [[ -n "${DLT_PARTITIONS:-}" ]]; then
  printf 'DLT_PARTITIONS is ignored: retry/DLT targets inherit their source partition count.\n' >&2
fi

if [[ ! "$RESILIENCE_RETENTION_MS" =~ ^[1-9][0-9]*$ ]]; then
  printf 'RESILIENCE_RETENTION_MS must be a positive integer.\n' >&2
  exit 2
fi
if [[ ! "$RESILIENCE_REPLICATION_FACTOR" =~ ^[1-9][0-9]*$ ]]; then
  printf 'RESILIENCE_REPLICATION_FACTOR must be a positive integer.\n' >&2
  exit 2
fi
if [[ ! "$RETRY_ATTEMPTS" =~ ^([2-9]|[1-9][0-9]+)$ ]]; then
  printf 'RETRY_ATTEMPTS must be an integer >= 2.\n' >&2
  exit 2
fi
if [[ ! "$RETRY_INITIAL_DELAY_MS" =~ ^[1-9][0-9]*$ || ! "$RETRY_MAX_DELAY_MS" =~ ^[1-9][0-9]*$ ]]; then
  printf 'Retry delay values must be positive integers.\n' >&2
  exit 2
fi
if ! awk -v multiplier="$RETRY_MULTIPLIER" 'BEGIN { exit !(multiplier >= 1) }'; then
  printf 'RETRY_MULTIPLIER must be a number >= 1.\n' >&2
  exit 2
fi
if [[ "$PROVISION_LEGACY_SHARED_RETRY_TOPICS" != "true" \
      && "$PROVISION_LEGACY_SHARED_RETRY_TOPICS" != "false" ]]; then
  printf 'PROVISION_LEGACY_SHARED_RETRY_TOPICS must be true or false.\n' >&2
  exit 2
fi
if [[ "$PROVISION_ORDER_PAYMENT_DLTS" != "true" \
      && "$PROVISION_ORDER_PAYMENT_DLTS" != "false" ]]; then
  printf 'PROVISION_ORDER_PAYMENT_DLTS must be true or false.\n' >&2
  exit 2
fi
if [[ "$PROVISION_ANALYTICS_RETRY_TOPICS" != "true" \
      && "$PROVISION_ANALYTICS_RETRY_TOPICS" != "false" ]]; then
  printf 'PROVISION_ANALYTICS_RETRY_TOPICS must be true or false.\n' >&2
  exit 2
fi
if [[ "$PROVISION_IDENTITY_RETRY_TOPICS" != "true" \
      && "$PROVISION_IDENTITY_RETRY_TOPICS" != "false" ]]; then
  printf 'PROVISION_IDENTITY_RETRY_TOPICS must be true or false.\n' >&2
  exit 2
fi

run_kafka_command() {
  local label="$1"
  shift
  local attempt
  local output

  # Kafka AdminClient can briefly time out while controller metadata settles
  # after a large manifest reconciliation. The commands routed through this
  # helper are reads or idempotent creates/config updates; a persistent error
  # remains a hard failure rather than allowing an incompletely provisioned
  # retry/DLT topology to look healthy.
  for ((attempt = 1; attempt <= 3; attempt++)); do
    if output="$("$@" 2>&1)"; then
      printf '%s\n' "$output"
      return 0
    fi
    if (( attempt < 3 )); then
      printf 'Transient Kafka command failure for %s (attempt %s/3); retrying.\n' \
        "$label" "$attempt" >&2
      sleep 2
    fi
  done

  printf 'Kafka command failed for %s after 3 attempts:\n%s\n' "$label" "$output" >&2
  return 1
}

topic_metadata() {
  local topic="$1"
  local description
  local partitions
  local replication_factor

  description="$(run_kafka_command "describe topic $topic" \
    "$KAFKA_TOPICS_BIN" \
    --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
    --describe --topic "$topic")"
  partitions="$(printf '%s\n' "$description" | awk '
    {
      for (i = 1; i <= NF; i++) {
        if ($i == "PartitionCount:") {
          print $(i + 1)
          exit
        }
        if (index($i, "PartitionCount:") == 1) {
          value = $i
          sub(/^PartitionCount:/, "", value)
          if (value != "") {
            print value
            exit
          }
        }
      }
    }')"
  replication_factor="$(printf '%s\n' "$description" | awk '
    {
      for (i = 1; i <= NF; i++) {
        if ($i == "ReplicationFactor:") {
          print $(i + 1)
          exit
        }
        if (index($i, "ReplicationFactor:") == 1) {
          value = $i
          sub(/^ReplicationFactor:/, "", value)
          if (value != "") {
            print value
            exit
          }
        }
      }
    }')"
  if [[ ! "$partitions" =~ ^[1-9][0-9]*$ || ! "$replication_factor" =~ ^[1-9][0-9]*$ ]]; then
    printf 'Unable to determine partition/replication metadata for existing source/topic %s.\n' "$topic" >&2
    exit 1
  fi
  printf '%s %s\n' "$partitions" "$replication_factor"
}

topic_partitions() {
  local metadata
  metadata="$(topic_metadata "$1")"
  printf '%s\n' "${metadata%% *}"
}

provision_topic() {
  local topic="$1"
  local partitions="$2"
  local metadata
  local existing_partitions
  local existing_replication_factor

  run_kafka_command "create topic $topic" \
    "$KAFKA_TOPICS_BIN" \
    --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
    --create --if-not-exists \
    --topic "$topic" \
    --partitions "$partitions" \
    --replication-factor "$RESILIENCE_REPLICATION_FACTOR" \
    --config "retention.ms=$RESILIENCE_RETENTION_MS" \
    --config cleanup.policy=delete

  # --create --if-not-exists does not reconcile an already auto-created topic.
  # Retry/DLT publishers retain the source partition, so a target with fewer
  # partitions would fail at the first poison/retry record. Kafka can increase
  # the target safely; it cannot shrink an existing topic.
  metadata="$(topic_metadata "$topic")"
  read -r existing_partitions existing_replication_factor <<< "$metadata"
  if (( existing_partitions < partitions )); then
    "$KAFKA_TOPICS_BIN" \
      --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
      --alter --topic "$topic" --partitions "$partitions"
  elif (( existing_partitions > partitions )); then
    printf 'Topic %s already has %s partitions (source has %s); retaining the larger target.\n' \
      "$topic" "$existing_partitions" "$partitions" >&2
  fi
  if (( existing_replication_factor < RESILIENCE_REPLICATION_FACTOR )); then
    printf 'Topic %s has replication factor %s, below required %s; complete a Kafka partition reassignment before retry/DLT use.\n' \
      "$topic" "$existing_replication_factor" "$RESILIENCE_REPLICATION_FACTOR" >&2
    exit 1
  fi

  # Reconcile retention even when a broker auto-created the topic before this
  # helper ran. This keeps DLT/retry retention deterministic across reruns.
  run_kafka_command "update config for topic $topic" \
    "$KAFKA_CONFIGS_BIN" \
    --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
    --entity-type topics \
    --entity-name "$topic" \
    --alter \
    --add-config "retention.ms=$RESILIENCE_RETENTION_MS,cleanup.policy=delete"
}

retry_delays=()
while IFS= read -r delay; do
  retry_delays+=("$delay")
done < <(awk \
  -v attempts="$RETRY_ATTEMPTS" \
  -v initial="$RETRY_INITIAL_DELAY_MS" \
  -v multiplier="$RETRY_MULTIPLIER" \
  -v maximum="$RETRY_MAX_DELAY_MS" \
  'BEGIN {
     delay = initial + 0
     for (attempt = 1; attempt < attempts; attempt++) {
       printf "%.0f\n", delay
       delay = delay * multiplier
       if (delay > maximum) {
         delay = maximum
       }
     }
   }')

if (( ${#retry_delays[@]} == 0 )); then
  printf 'Retry policy produced no retry delays.\n' >&2
  exit 2
fi

provision_retry_source() {
  local source="$1"
  local retry_suffix="$2"
  local dlt_suffix="$3"
  local source_partitions
  local delay

  source_partitions="$(topic_partitions "$source")"
  printf 'Provisioning source=%s partitions=%s retry_suffix=%s dlt_suffix=%s\n' \
    "$source" "$source_partitions" "$retry_suffix" "$dlt_suffix"
  for delay in "${retry_delays[@]}"; do
    provision_topic "${source}${retry_suffix}-${delay}" "$source_partitions"
  done
  provision_topic "${source}${dlt_suffix}" "$source_partitions"
}

provision_match_retry_source() {
  local source="$1"
  local source_partitions
  local delay
  local match_retry_delays=()

  IFS=',' read -r -a match_retry_delays <<< "$MATCH_RETRY_DELAYS_MS"
  if (( ${#match_retry_delays[@]} == 0 )); then
    printf 'MATCH_RETRY_DELAYS_MS must contain at least one delay.\n' >&2
    exit 2
  fi
  source_partitions="$(topic_partitions "$source")"
  printf 'Provisioning Match source=%s partitions=%s retry_suffix=.retry dlt_suffix=.DLT\n' \
    "$source" "$source_partitions"
  for delay in "${match_retry_delays[@]}"; do
    if [[ ! "$delay" =~ ^[1-9][0-9]*$ ]]; then
      printf 'MATCH_RETRY_DELAYS_MS contains an invalid delay: %s\n' "$delay" >&2
      exit 2
    fi
    provision_topic "${source}.retry-${delay}" "$source_partitions"
  done
  provision_topic "${source}.DLT" "$source_partitions"
}

# Sources that still use the generic standard suffix. The owning service's
# @RetryableTopic annotation is the source of truth for every mapping below.
standard_retry_sources=(
  delivery.completed
  saga.command.create-delivery
  saga.command.cancel-delivery
  saga.command.cache-shipper-found
  saga.command.expire-shipper-offer
  saga.command.mark-shipper-not-found
)

# Saga, Order, Notification and the feature-gated Promotion/Flash-sale
# reservation consumers share source topics. They use custom suffixes so Spring
# Kafka never binds one retry/DLT topology to a different listener owner. Keep
# these lists in sync with their annotations.
saga_retry_sources=(
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
order_retry_sources=(
  restaurant.order-confirmed
  restaurant.order-rejected
  saga.command.update-order-status
)
notification_retry_sources=(
  order.created
  delivery.status-updated
  delivery.shipper-offered
)
promotion_retry_sources=(
  order.created
  order.cancelled
  order.refund-eligible
)
flashsale_retry_sources=(
  order.created
  order.cancelled
  order.refund-eligible
)
analytics_retry_sources=(
  order.created
  order.status-updated
  order.cancelled
)
tracking_retry_sources=(
  shipper.location-updated
)
# Tracking routes shipper status facts to its own DLT through a blocking
# listener factory. Match also consumes this source and keeps source.DLT, so
# provision both destinations rather than collapsing two independent owners.
tracking_dlt_only_sources=(
  shipper.status-change
)
# Auth, User, Shipper, Delivery and Tracking each own an isolated retry/DLT
# topology on identity contracts. The suffixes mirror their @RetryableTopic
# annotations exactly; do not merge them even though source topics are shared.
auth_identity_retry_sources=(
  identity.profile.created
)
user_identity_retry_sources=(
  identity.status.changed
)
shipper_identity_retry_sources=(
  identity.status.changed
)
delivery_shipper_identity_retry_sources=(
  shipper.identity.upserted
)
tracking_shipper_identity_retry_sources=(
  shipper.identity.upserted
)

# Generic names emitted by the pre-isolation annotations. These are migration
# compatibility targets, not destinations for new code. Existing old replicas
# continue consuming them during the rolling drain; do not turn this off until
# lag is zero and the maximum configured retry delay has elapsed.
legacy_shared_retry_sources=(
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

# Match's Find listener has an explicit ".retry" suffix and the stop/location/
# status listeners use the normal source.DLT error-handler route.
for source in "${standard_retry_sources[@]}"; do
  provision_retry_source "$source" "-retry" ".DLT"
done
for source in "${saga_retry_sources[@]}"; do
  provision_retry_source "$source" "-retry-saga" ".saga.DLT"
done
for source in "${order_retry_sources[@]}"; do
  provision_retry_source "$source" "-retry-order" ".order.DLT"
done
for source in "${notification_retry_sources[@]}"; do
  provision_retry_source "$source" "-retry-notification" ".notification.DLT"
done
for source in "${promotion_retry_sources[@]}"; do
  provision_retry_source "$source" "-retry-promotion" ".promotion.DLT"
done
for source in "${flashsale_retry_sources[@]}"; do
  provision_retry_source "$source" "-retry-flashsale" ".flashsale.DLT"
done
if [[ "$PROVISION_ANALYTICS_RETRY_TOPICS" == "true" ]]; then
  for source in "${analytics_retry_sources[@]}"; do
    provision_retry_source "$source" "-retry-analytics" ".analytics.DLT"
  done
fi
for source in "${tracking_retry_sources[@]}"; do
  provision_retry_source "$source" "-retry-tracking" ".tracking.DLT"
done
for source in "${tracking_dlt_only_sources[@]}"; do
  source_partitions="$(topic_partitions "$source")"
  provision_topic "${source}.tracking.DLT" "$source_partitions"
done
if [[ "$PROVISION_IDENTITY_RETRY_TOPICS" == "true" ]]; then
  for source in "${auth_identity_retry_sources[@]}"; do
    provision_retry_source "$source" "-retry-auth-identity" ".auth-identity.DLT"
  done
  for source in "${user_identity_retry_sources[@]}"; do
    provision_retry_source "$source" "-retry-user-identity" ".user-identity.DLT"
  done
  for source in "${shipper_identity_retry_sources[@]}"; do
    provision_retry_source "$source" "-retry-shipper-identity" ".shipper-identity.DLT"
  done
  for source in "${delivery_shipper_identity_retry_sources[@]}"; do
    provision_retry_source "$source" "-retry-delivery-shipper-identity" ".delivery-shipper-identity.DLT"
  done
  for source in "${tracking_shipper_identity_retry_sources[@]}"; do
    provision_retry_source "$source" "-retry-tracking-shipper-identity" ".tracking-shipper-identity.DLT"
  done
fi
if [[ "$PROVISION_LEGACY_SHARED_RETRY_TOPICS" == "true" ]]; then
  printf 'Provisioning legacy shared retry/DLT targets for the rolling drain.\n'
  for source in "${legacy_shared_retry_sources[@]}"; do
    provision_retry_source "$source" "-retry" ".DLT"
  done
fi
provision_match_retry_source "saga.command.find-shipper"

# Normal (blocking-retry) listeners that publish source.DLT directly. Some
# sources also have a non-blocking retry owner above; provisioning is
# intentionally idempotent in that case.
dlt_only_sources=(
  order.refund-eligible
  saga.command.stop-matching
  shipper.location-updated
  shipper.status-change
)
for source in "${dlt_only_sources[@]}"; do
  source_partitions="$(topic_partitions "$source")"
  provision_topic "${source}.DLT" "$source_partitions"
done

if [[ "$PROVISION_ANALYTICS_RETRY_TOPICS" == "true" \
      && "$PROVISION_ORDER_PAYMENT_DLTS" == "true" ]]; then
  for source in payment.completed payment.failed; do
    source_partitions="$(topic_partitions "$source")"
    provision_topic "${source}.order.DLT" "$source_partitions"
    if [[ "$PROVISION_LEGACY_SHARED_RETRY_TOPICS" == "true" ]]; then
      provision_topic "${source}.DLT" "$source_partitions"
    fi
  done
fi

# Analytics payment listeners are also default-off with the payment graph.
# Provision their recovery targets only as part of an explicitly approved
# payment/analytics activation.
if [[ "$PROVISION_ORDER_PAYMENT_DLTS" == "true" ]]; then
  for source in payment.completed payment.failed; do
    provision_retry_source "$source" "-retry-analytics" ".analytics.DLT"
  done
fi

printf 'Provisioned canonical retry/DLT topics with retention.ms=%s and source-matched partition counts.\n' \
  "$RESILIENCE_RETENTION_MS"
