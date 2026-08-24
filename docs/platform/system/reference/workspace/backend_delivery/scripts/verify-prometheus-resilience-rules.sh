#!/usr/bin/env bash
set -euo pipefail

# Static verification for the repository-owned DLT alert policy. This proves
# that the mounted Prometheus config can load the exact approved alert rule; it
# does not claim that a production alert route, Kafka exporter, or SLO policy
# exists in an external platform.

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly PROMETHEUS_DIR="$ROOT_DIR/monitoring/prometheus"
readonly PROMETHEUS_IMAGE="${PROMETHEUS_IMAGE:-prom/prometheus:v3.5.0}"
readonly RULE_FILE="$PROMETHEUS_DIR/rules/resilience.yml"
readonly CONFIG_FILE="$PROMETHEUS_DIR/prometheus.yml"
readonly RULE_TEST_FILE="$PROMETHEUS_DIR/tests/resilience-rules.test.yml"

command -v docker >/dev/null
[[ -r "$RULE_FILE" && -r "$CONFIG_FILE" && -r "$RULE_TEST_FILE" ]]

# Guard the operating policy as well as YAML syntax. The rule is intentionally
# threshold-free beyond any new DLT record: replay requires an operator review,
# so one recovered record must be visible immediately.
if ! rg -U -Fq $'alert: DeliveryKafkaDltIncreasing\n        expr: increase(delivery_kafka_events_total{event="dlt"}[5m]) > 0\n        for: 0m' \
    "$RULE_FILE"; then
  printf 'Prometheus resilience rule must alert on every five-minute DLT increase without an added delay.\n' >&2
  exit 1
fi
if ! rg -Fq '/etc/prometheus/rules/*.yml' "$CONFIG_FILE"; then
  printf 'Prometheus config does not mount the resilience rule glob.\n' >&2
  exit 1
fi

docker image inspect "$PROMETHEUS_IMAGE" >/dev/null 2>&1 || {
  printf 'Required local Prometheus image is unavailable: %s\n' "$PROMETHEUS_IMAGE" >&2
  exit 1
}

docker run --rm --pull=never --network none --entrypoint promtool \
  -v "$PROMETHEUS_DIR:/etc/prometheus:ro" \
  "$PROMETHEUS_IMAGE" check config /etc/prometheus/prometheus.yml
docker run --rm --pull=never --network none --entrypoint promtool \
  -v "$PROMETHEUS_DIR:/etc/prometheus:ro" \
  "$PROMETHEUS_IMAGE" check rules /etc/prometheus/rules/resilience.yml
docker run --rm --pull=never --network none --entrypoint promtool \
  -v "$PROMETHEUS_DIR:/etc/prometheus:ro" \
  "$PROMETHEUS_IMAGE" test rules /etc/prometheus/tests/resilience-rules.test.yml

printf 'Prometheus resilience rule syntax and approved DLT-alert policy are valid.\n'
