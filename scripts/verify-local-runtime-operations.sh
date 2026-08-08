#!/usr/bin/env bash
set -euo pipefail

# Static safety gate for runbooks that operate on a developer's live Compose
# stack. Runtime recovery must never silently tear down/recreate the database
# or Elasticsearch container just because Compose defaults differ from the
# currently mounted project.

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly SEARCH_RECOVERY="$ROOT_DIR/scripts/recover-local-search.sh"
readonly OBSERVABILITY_VERIFY="$ROOT_DIR/scripts/verify-observability-runtime.sh"

bash -n "$SEARCH_RECOVERY" "$OBSERVABILITY_VERIFY"

for required in \
  'COMPOSE_FILES' \
  'POSTGRES_HOST_PORT' \
  'POSTGRES_VOLUME_NAME' \
  'KAFKA_VOLUME_NAME' \
  'compose start elasticsearch' \
  'compose start search-service'; do
  rg -Fq "$required" "$SEARCH_RECOVERY" || {
    echo "Search recovery safety contract is missing: $required" >&2
    exit 1
  }
done

if rg -n '^[[:space:]]*compose[[:space:]]+(up|down)\b|^[[:space:]]*docker[[:space:]]+(rm|volume[[:space:]]+rm)\b' "$SEARCH_RECOVERY"; then
  echo "Search recovery must not recreate or remove containers/volumes." >&2
  exit 1
fi

for required in \
  'delivery-prometheus' \
  'delivery-grafana' \
  'delivery-services' \
  'delivery-operations' \
  'OBSERVABILITY_TARGET_TIMEOUT_SECONDS'; do
  rg -Fq "$required" "$OBSERVABILITY_VERIFY" || {
    echo "Observability verifier contract is missing: $required" >&2
    exit 1
  }
done

echo "Local runtime recovery and observability script safety contracts are valid."
