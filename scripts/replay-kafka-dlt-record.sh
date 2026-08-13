#!/usr/bin/env bash
set -euo pipefail

# Operator-only wrapper for the packaged DLT single-record replay command. The
# Java tool itself refuses a missing/incorrect DLT coordinate confirmation and
# defaults to dry-run. This wrapper intentionally does not print environment
# values so a command-config secret path or SASL setting cannot leak to logs.

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly TOOL_JAR="${KAFKA_DLT_REPLAY_JAR:-$ROOT_DIR/kafka-operations-tool/target/kafka-operations-tool-1.0.0-SNAPSHOT.jar}"

if [[ ! -r "$TOOL_JAR" ]]; then
  printf 'Kafka DLT replay jar is not readable. Build it with: mvn -pl kafka-operations-tool -am package\n' >&2
  exit 2
fi

exec java -jar "$TOOL_JAR"
