#!/usr/bin/env bash
set -euo pipefail

# Static contract gate for the operator wrapper/runbook boundary. Kafka behavior
# is covered by kafka-operations-tool's Testcontainers integration test; this
# script ensures the runnable entry point cannot silently become a raw console
# producer or an unconfirmed bulk-replay shortcut.

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly WRAPPER="$ROOT_DIR/scripts/replay-kafka-dlt-record.sh"
readonly RUNBOOK="$ROOT_DIR/docs/runbooks/resilience-operations.md"

bash -n "$WRAPPER"

for required in \
  'KAFKA_DLT_REPLAY_JAR' \
  'mvn -pl kafka-operations-tool -am package' \
  'exec java -jar'; do
  rg -Fq "$required" "$WRAPPER" || {
    printf 'Kafka DLT replay wrapper is missing its required safety contract: %s\n' "$required" >&2
    exit 1
  }
done

for required in \
  'DLT_REPLAY_CONFIRMATION' \
  'DLT_REPLAY_DRY_RUN=false' \
  'has no bulk mode' \
  'neither grants ACLs nor changes consumer' \
  'KAFKA_COMMAND_CONFIG'; do
  rg -Fq "$required" "$RUNBOOK" || {
    printf 'Kafka DLT replay runbook is missing its required operational guard: %s\n' "$required" >&2
    exit 1
  }
done

if rg -n 'kafka-console-producer|kafka-reassign-partitions|--reset-offsets' "$WRAPPER"; then
  printf 'Kafka DLT replay wrapper must delegate only to the guarded Java tool.\n' >&2
  exit 1
fi

printf 'Kafka DLT single-record replay wrapper/runbook safety contract is valid.\n'
