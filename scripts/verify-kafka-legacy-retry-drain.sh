#!/usr/bin/env bash
set -euo pipefail

# Read-only promotion gate for the shared-source retry migration. New Saga,
# Order and Notification listeners no longer subscribe to the generic
# <source>-retry-* topics. Those topics must not be removed from the manifest
# until their old consumer assignments have disappeared, their offsets have
# reached every partition's log end, and no producer writes another legacy
# retry record during one full maximum retry delay.

: "${KAFKA_BOOTSTRAP_SERVERS:?Set KAFKA_BOOTSTRAP_SERVERS for the approved broker}"

KAFKA_CONSUMER_GROUPS_BIN="${KAFKA_CONSUMER_GROUPS_BIN:-kafka-consumer-groups.sh}"
KAFKA_RUN_CLASS_BIN="${KAFKA_RUN_CLASS_BIN:-kafka-run-class.sh}"
KAFKA_COMMAND_CONFIG="${KAFKA_COMMAND_CONFIG:-}"
SAGA_GROUP="${SAGA_LEGACY_CONSUMER_GROUP:-saga-orchestrator}"
ORDER_GROUP="${ORDER_LEGACY_CONSUMER_GROUP:-order-service-group}"
NOTIFICATION_GROUP="${NOTIFICATION_LEGACY_CONSUMER_GROUP:-notification-service-group}"
RETRY_ATTEMPTS="${KAFKA_RETRY_ATTEMPTS:-4}"
RETRY_INITIAL_DELAY_MS="${KAFKA_RETRY_INITIAL_DELAY_MS:-1000}"
RETRY_MULTIPLIER="${KAFKA_RETRY_MULTIPLIER:-2.0}"
MAX_RETRY_DELAY_MS="${KAFKA_RETRY_MAX_DELAY_MS:-10000}"
QUIET_WINDOW_SECONDS="${KAFKA_LEGACY_DRAIN_QUIET_WINDOW_SECONDS:-}"

if [[ -z "$QUIET_WINDOW_SECONDS" ]]; then
  QUIET_WINDOW_SECONDS=$(((MAX_RETRY_DELAY_MS + 999) / 1000))
fi

if [[ ! "$RETRY_ATTEMPTS" =~ ^([2-9]|[1-9][0-9]+)$ ]]; then
  printf 'KAFKA_RETRY_ATTEMPTS must be an integer >= 2.\n' >&2
  exit 2
fi
if [[ ! "$RETRY_INITIAL_DELAY_MS" =~ ^[1-9][0-9]*$ \
      || ! "$MAX_RETRY_DELAY_MS" =~ ^[1-9][0-9]*$ ]]; then
  printf 'KAFKA_RETRY_INITIAL_DELAY_MS and KAFKA_RETRY_MAX_DELAY_MS must be positive integers.\n' >&2
  exit 2
fi
if ! awk -v multiplier="$RETRY_MULTIPLIER" 'BEGIN { exit !(multiplier >= 1) }'; then
  printf 'KAFKA_RETRY_MULTIPLIER must be a number >= 1.\n' >&2
  exit 2
fi
if [[ ! "$QUIET_WINDOW_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  printf 'KAFKA_LEGACY_DRAIN_QUIET_WINDOW_SECONDS must be a positive integer.\n' >&2
  exit 2
fi
required_quiet_seconds=$(((MAX_RETRY_DELAY_MS + 999) / 1000))
if (( QUIET_WINDOW_SECONDS < required_quiet_seconds )); then
  printf 'KAFKA_LEGACY_DRAIN_QUIET_WINDOW_SECONDS=%s is below the required maximum retry delay of %ss.\n' \
    "$QUIET_WINDOW_SECONDS" "$required_quiet_seconds" >&2
  exit 2
fi
if [[ -n "$KAFKA_COMMAND_CONFIG" && ! -r "$KAFKA_COMMAND_CONFIG" ]]; then
  printf 'KAFKA_COMMAND_CONFIG is not readable: %s\n' "$KAFKA_COMMAND_CONFIG" >&2
  exit 2
fi

command -v "$KAFKA_CONSUMER_GROUPS_BIN" >/dev/null
command -v "$KAFKA_RUN_CLASS_BIN" >/dev/null

kafka_groups_command() {
  local group="$1"
  if [[ -n "$KAFKA_COMMAND_CONFIG" ]]; then
    "$KAFKA_CONSUMER_GROUPS_BIN" \
      --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
      --describe --group "$group" --command-config "$KAFKA_COMMAND_CONFIG"
  else
    "$KAFKA_CONSUMER_GROUPS_BIN" \
      --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
      --describe --group "$group"
  fi
}

kafka_end_offsets_command() {
  local topic="$1"
  # GetOffsetShell supports --command-config on the Kafka version used by this
  # platform. Keep it out of the default invocation so the verifier also works
  # with plain-text admin toolboxes.
  if [[ -n "$KAFKA_COMMAND_CONFIG" ]]; then
    "$KAFKA_RUN_CLASS_BIN" kafka.tools.GetOffsetShell \
      --broker-list "$KAFKA_BOOTSTRAP_SERVERS" \
      --topic "$topic" --time -1 --command-config "$KAFKA_COMMAND_CONFIG"
  else
    "$KAFKA_RUN_CLASS_BIN" kafka.tools.GetOffsetShell \
      --broker-list "$KAFKA_BOOTSTRAP_SERVERS" \
      --topic "$topic" --time -1
  fi
}

# These are the generic destinations subscribed by pre-isolation replicas.
# Keep the maps aligned with the active owner-isolated @RetryableTopic
# listeners; DLTs are deliberately excluded because they remain retained for
# their full evidence/replay lifetime after retry queues have drained.
saga_legacy_sources=(
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
)
order_legacy_sources=(
  restaurant.order-confirmed
  restaurant.order-rejected
  saga.command.update-order-status
)
notification_legacy_sources=(
  order.created
  delivery.status-updated
  delivery.shipper-offered
)
# Match the generic topic names produced by the pre-isolation annotations.
# This is deliberately calculated from the same KAFKA_RETRY_* inputs as the
# provisioner: operators changing that topology must run the drain gate with
# the retired replicas' effective retry policy, not silently inspect the
# default three topics.
retry_delays=()
while IFS= read -r delay; do
  retry_delays+=("$delay")
done < <(awk \
  -v attempts="$RETRY_ATTEMPTS" \
  -v initial="$RETRY_INITIAL_DELAY_MS" \
  -v multiplier="$RETRY_MULTIPLIER" \
  -v maximum="$MAX_RETRY_DELAY_MS" \
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
  printf 'Kafka retry policy produced no legacy retry delays.\n' >&2
  exit 2
fi

run_read() {
  local label="$1"
  shift
  local attempt
  local output
  for ((attempt = 1; attempt <= 3; attempt++)); do
    if output="$("$@" 2>&1)"; then
      printf '%s\n' "$output"
      return 0
    fi
    if (( attempt < 3 )); then
      printf 'Transient Kafka read failure for %s (attempt %s/3); retrying.\n' \
        "$label" "$attempt" >&2
      sleep 2
    fi
  done
  printf 'Kafka read failed for %s after 3 attempts:\n%s\n' "$label" "$output" >&2
  return 1
}

describe_group() {
  local group="$1"
  run_read "consumer group $group" kafka_groups_command "$group"
}

topic_end_offsets() {
  local topic="$1"
  local offsets
  offsets="$(run_read "end offsets for $topic" kafka_end_offsets_command "$topic")"
  offsets="$(printf '%s\n' "$offsets" | awk -F: -v topic="$topic" '
    $1 == topic && $2 ~ /^[0-9]+$/ && $3 ~ /^[0-9]+$/ { print $1 ":" $2 ":" $3 }
  ' | sort -t: -k2,2n)"
  if [[ -z "$offsets" ]]; then
    printf 'Kafka did not return a numeric end offset for legacy retry topic %s.\n' "$topic" >&2
    return 1
  fi
  printf '%s\n' "$offsets"
}

all_legacy_topics() {
  local source delay
  for source in "${saga_legacy_sources[@]}" \
                "${order_legacy_sources[@]}" \
                "${notification_legacy_sources[@]}"; do
    for delay in "${retry_delays[@]}"; do
      printf '%s-retry-%s\n' "$source" "$delay"
    done
  done | sort -u
}

offset_snapshot() {
  local topic offsets
  while IFS= read -r topic; do
    if ! offsets="$(topic_end_offsets "$topic")"; then
      return 1
    fi
    printf '%s\n' "$offsets"
  done <<< "$(all_legacy_topics)"
}

verify_owner_topic() {
  local base_group="$1"
  local owner="$2"
  local topic="$3"
  local description offset_line partition end_offset row current_offset observed_end lag consumer_id offsets

  if ! description="$(describe_group "$base_group")"; then
    printf 'Cannot inspect %s consumer group %s for legacy topic %s; fail closed.\n' \
      "$owner" "$base_group" "$topic" >&2
    return 1
  fi

  if ! offsets="$(topic_end_offsets "$topic")"; then
    return 1
  fi
  while IFS= read -r offset_line; do
    partition="${offset_line#*:}"
    partition="${partition%%:*}"
    end_offset="${offset_line##*:}"
    row="$(printf '%s\n' "$description" | awk -v topic="$topic" -v partition="$partition" '
      $2 == topic && $3 == partition { print; exit }
    ')"
    if [[ -z "$row" ]]; then
      if (( end_offset > 0 )); then
        printf '%s consumer group %s has no committed offset for non-empty legacy topic %s partition %s (end=%s).\n' \
          "$owner" "$base_group" "$topic" "$partition" "$end_offset" >&2
        return 1
      fi
      continue
    fi

    read -r _ _ _ current_offset observed_end lag consumer_id _ _ <<< "$row"
    if [[ "$consumer_id" != "-" && -n "$consumer_id" ]]; then
      printf '%s consumer group %s still has an active consumer assigned to legacy topic %s partition %s: %s.\n' \
        "$owner" "$base_group" "$topic" "$partition" "$consumer_id" >&2
      return 1
    fi
    if [[ "$current_offset" == "-" && "$observed_end" == "0" && "$lag" == "-" ]]; then
      continue
    fi
    if [[ ! "$current_offset" =~ ^[0-9]+$ || ! "$observed_end" =~ ^[0-9]+$ \
          || ! "$lag" =~ ^[0-9]+$ ]]; then
      printf '%s consumer group %s returned an unparseable legacy offset row for %s partition %s: %s\n' \
        "$owner" "$base_group" "$topic" "$partition" "$row" >&2
      return 1
    fi
    if [[ "$observed_end" != "$end_offset" ]]; then
      printf '%s consumer group %s observed a moving legacy log end for %s partition %s (%s != %s); rerun after it is quiet.\n' \
        "$owner" "$base_group" "$topic" "$partition" "$observed_end" "$end_offset" >&2
      return 1
    fi
    if (( lag != 0 || current_offset != end_offset )); then
      printf '%s consumer group %s has legacy retry lag for %s partition %s: current=%s end=%s lag=%s.\n' \
        "$owner" "$base_group" "$topic" "$partition" "$current_offset" "$end_offset" "$lag" >&2
      return 1
    fi
  done <<< "$offsets"

  # These listeners have no @KafkaListener groupId. Spring Kafka only suffixes
  # an endpoint-level group id for non-blocking retry containers; with no
  # endpoint group the retry containers inherit the ConsumerFactory group-id.
  # A successfully described base group can have no row for an empty topic
  # after the migration; that is the desired drained state. A non-empty topic
  # without a committed row was rejected above because it cannot prove that an
  # old record was consumed.
}

verify_owner_drain() {
  local base_group="$1"
  local owner="$2"
  shift 2
  local source delay topic

  for source in "$@"; do
    for delay in "${retry_delays[@]}"; do
      topic="${source}-retry-${delay}"
      verify_owner_topic "$base_group" "$owner" "$topic"
    done
  done
}

sleep_quiet_window() {
  local remaining="$QUIET_WINDOW_SECONDS"
  local slice
  while (( remaining > 0 )); do
    slice=$(( remaining > 30 ? 30 : remaining ))
    sleep "$slice"
    remaining=$((remaining - slice))
  done
}

printf '[LEGACY-RETRY-DRAIN] inspect zero lag and inactive old assignments\n'
verify_owner_drain "$SAGA_GROUP" Saga "${saga_legacy_sources[@]}"
verify_owner_drain "$ORDER_GROUP" Order "${order_legacy_sources[@]}"
verify_owner_drain "$NOTIFICATION_GROUP" Notification "${notification_legacy_sources[@]}"
baseline_offsets="$(offset_snapshot)"

printf '[LEGACY-RETRY-DRAIN] wait %ss (maximum configured retry delay is %sms)\n' \
  "$QUIET_WINDOW_SECONDS" "$MAX_RETRY_DELAY_MS"
sleep_quiet_window

printf '[LEGACY-RETRY-DRAIN] recheck zero lag, inactive assignments and unchanged end offsets\n'
verify_owner_drain "$SAGA_GROUP" Saga "${saga_legacy_sources[@]}"
verify_owner_drain "$ORDER_GROUP" Order "${order_legacy_sources[@]}"
verify_owner_drain "$NOTIFICATION_GROUP" Notification "${notification_legacy_sources[@]}"
final_offsets="$(offset_snapshot)"
if [[ "$final_offsets" != "$baseline_offsets" ]]; then
  printf '%s\n' 'Legacy retry topic end offsets advanced during the quiet window; an old producer may still be writing.' >&2
  exit 1
fi

printf 'PASS: legacy generic retry drain is zero-lag, has no active old consumer assignment, and received no writes for %ss. Record this output before setting PROVISION_LEGACY_SHARED_RETRY_TOPICS=false.\n' \
  "$QUIET_WINDOW_SECONDS"
