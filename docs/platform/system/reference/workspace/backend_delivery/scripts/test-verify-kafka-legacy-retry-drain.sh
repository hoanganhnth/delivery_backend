#!/usr/bin/env bash
set -euo pipefail

# Focused contract test for the production-read-only legacy retry drain gate.
# It replaces Kafka CLI binaries with deterministic fixtures so parser and
# fail-closed behavior can be validated without a production broker.

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly VERIFY_SCRIPT="$ROOT_DIR/scripts/verify-kafka-legacy-retry-drain.sh"

work_dir="$(mktemp -d)"
cleanup() {
  rm -rf "$work_dir"
}
trap cleanup EXIT INT TERM

cat > "$work_dir/kafka-consumer-groups.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
group=""
while (( $# > 0 )); do
  case "$1" in
    --group) group="$2"; shift 2 ;;
    *) shift ;;
  esac
done
if [[ -n "${FIXTURE_GROUP_LOG:-}" ]]; then
  printf '%s\n' "$group" >> "$FIXTURE_GROUP_LOG"
fi
case "${FIXTURE_MODE:-pass}:${group}" in
  pass:*|advance:*)
    printf 'GROUP TOPIC PARTITION CURRENT-OFFSET LOG-END-OFFSET LAG CONSUMER-ID HOST CLIENT-ID\n'
    ;;
  active:*)
    printf 'GROUP TOPIC PARTITION CURRENT-OFFSET LOG-END-OFFSET LAG CONSUMER-ID HOST CLIENT-ID\n'
    if [[ "$group" == "saga-orchestrator" ]]; then
      printf '%s order.created-retry-1000 0 0 0 0 old-replica /10.0.0.8 consumer-old\n' "$group"
    fi
    ;;
  lag:*)
    printf 'GROUP TOPIC PARTITION CURRENT-OFFSET LOG-END-OFFSET LAG CONSUMER-ID HOST CLIENT-ID\n'
    if [[ "$group" == "saga-orchestrator" ]]; then
      printf '%s order.created-retry-1000 0 0 1 1 - - -\n' "$group"
    fi
    ;;
  missing:*)
    printf 'Consumer group does not exist.\n' >&2
    exit 1
    ;;
  *)
    printf 'Unknown fixture mode %s\n' "${FIXTURE_MODE:-}" >&2
    exit 2
    ;;
esac
EOF

cat > "$work_dir/kafka-run-class.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
topic=""
while (( $# > 0 )); do
  case "$1" in
    --topic) topic="$2"; shift 2 ;;
    *) shift ;;
  esac
done
if [[ "${FIXTURE_MODE:-pass}" == "read-failure" ]]; then
  printf 'simulated Kafka metadata failure\n' >&2
  exit 1
fi
phase="${FIXTURE_PHASE:-first}"
if [[ -n "${FIXTURE_PHASE_FILE:-}" && -r "${FIXTURE_PHASE_FILE}" ]]; then
  phase="$(<"${FIXTURE_PHASE_FILE}")"
fi
if [[ -n "${FIXTURE_TOPIC_LOG:-}" ]]; then
  printf '%s\n' "$topic" >> "$FIXTURE_TOPIC_LOG"
fi
offset=0
if [[ "${FIXTURE_MODE:-pass}" == "advance" && "$phase" == "second" \
      && "$topic" == "order.created-retry-1000" ]]; then
  offset=1
fi
printf '%s:0:%s\n' "$topic" "$offset"
EOF
chmod +x "$work_dir/kafka-consumer-groups.sh" "$work_dir/kafka-run-class.sh"

run_fixture() {
  local mode="$1"
  shift
  FIXTURE_MODE="$mode" \
  FIXTURE_PHASE="${FIXTURE_PHASE:-first}" \
  KAFKA_BOOTSTRAP_SERVERS=fixture:9092 \
  KAFKA_CONSUMER_GROUPS_BIN="$work_dir/kafka-consumer-groups.sh" \
  KAFKA_RUN_CLASS_BIN="$work_dir/kafka-run-class.sh" \
  KAFKA_LEGACY_DRAIN_QUIET_WINDOW_SECONDS=1 \
  KAFKA_RETRY_MAX_DELAY_MS=1000 \
    bash "$VERIFY_SCRIPT" "$@"
}

group_log="$work_dir/groups.log"
pass_output="$(FIXTURE_GROUP_LOG="$group_log" run_fixture pass)"
printf '%s\n' "$pass_output" | grep -Fq 'PASS: legacy generic retry drain is zero-lag'
expected_groups=$'notification-service-group\norder-service-group\nsaga-orchestrator'
observed_groups="$(sort -u "$group_log")"
if [[ "$observed_groups" != "$expected_groups" ]]; then
  printf 'Legacy drain verifier did not inspect exactly the configured base consumer groups:\n%s\n' \
    "$observed_groups" >&2
  exit 1
fi

custom_topic_log="$work_dir/custom-topics.log"
custom_policy_output="$(
  FIXTURE_TOPIC_LOG="$custom_topic_log" \
  KAFKA_RETRY_ATTEMPTS=3 \
  KAFKA_RETRY_INITIAL_DELAY_MS=500 \
  KAFKA_RETRY_MULTIPLIER=3 \
  KAFKA_RETRY_MAX_DELAY_MS=1000 \
    run_fixture pass
)"
printf '%s\n' "$custom_policy_output" | grep -Fq 'PASS: legacy generic retry drain is zero-lag'
if grep -Ev -- '-retry-(500|1000)$' "$custom_topic_log" >/dev/null; then
  printf 'Legacy drain verifier did not derive retry topics from the supplied retry policy.\n' >&2
  exit 1
fi
if ! grep -Fqx 'order.created-retry-500' "$custom_topic_log" \
    || ! grep -Fqx 'order.created-retry-1000' "$custom_topic_log"; then
  printf 'Legacy drain verifier missed an expected retry topic from the supplied retry policy.\n' >&2
  exit 1
fi

if active_output="$(run_fixture active 2>&1)"; then
  printf '%s\n' 'Legacy drain verifier accepted an active old consumer.' >&2
  exit 1
fi
printf '%s\n' "$active_output" | grep -Fq 'still has an active consumer assigned'

if lag_output="$(run_fixture lag 2>&1)"; then
  printf '%s\n' 'Legacy drain verifier accepted non-zero lag.' >&2
  exit 1
fi
printf '%s\n' "$lag_output" | grep -Fq 'has legacy retry lag'

if read_output="$(run_fixture read-failure 2>&1)"; then
  printf '%s\n' 'Legacy drain verifier ignored a Kafka end-offset read failure.' >&2
  exit 1
fi
printf '%s\n' "$read_output" | grep -Fq 'Kafka read failed for end offsets'

phase_file="$work_dir/phase"
printf '%s\n' first > "$phase_file"
if quiet_output="$(
  FIXTURE_MODE=advance \
  FIXTURE_PHASE_FILE="$phase_file" \
  KAFKA_BOOTSTRAP_SERVERS=fixture:9092 \
  KAFKA_CONSUMER_GROUPS_BIN="$work_dir/kafka-consumer-groups.sh" \
  KAFKA_RUN_CLASS_BIN="$work_dir/kafka-run-class.sh" \
  KAFKA_LEGACY_DRAIN_QUIET_WINDOW_SECONDS=1 \
  KAFKA_RETRY_MAX_DELAY_MS=1000 \
  bash -c '
    "$0" & runner=$!
    sleep 0.2
    printf "%s\\n" second > "$1"
    wait "$runner"
  ' "$VERIFY_SCRIPT" "$phase_file" 2>&1
)"; then
  printf '%s\n' 'Legacy drain verifier accepted a retry topic write during its quiet window.' >&2
  exit 1
fi
printf '%s\n' "$quiet_output" | grep -Fq 'Legacy retry topic end offsets advanced during the quiet window'

printf '%s\n' 'Kafka legacy retry drain verifier contract tests passed.'
