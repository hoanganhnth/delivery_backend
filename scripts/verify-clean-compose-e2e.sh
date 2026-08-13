#!/usr/bin/env bash
set -euo pipefail

# Runs the full COD flow against a disposable Compose project, fresh PostgreSQL /
# Kafka volumes and a dynamically assigned loopback Gateway port. It never stops,
# recreates or mounts the developer's canonical Compose stack.

readonly RUN_ID="${CLEAN_RUN_ID:-$(date +%Y%m%d%H%M%S)-$$}"
readonly CLEAN_PROJECT_NAME="delivery_e2e_${RUN_ID//-/_}"
readonly CLEAN_POSTGRES_VOLUME="delivery_e2e_${RUN_ID}_postgres_data"
readonly CLEAN_KAFKA_VOLUME="delivery_e2e_${RUN_ID}_kafka_data"
readonly CLEAN_MATCHING_MAX_RETRY_ATTEMPTS="${CLEAN_MATCHING_MAX_RETRY_ATTEMPTS:-2}"
readonly CLEAN_MATCHING_DELAY_SECONDS="${CLEAN_MATCHING_DELAY_SECONDS:-1}"
readonly CLEAN_MATCHING_MAX_DELAY_SECONDS="${CLEAN_MATCHING_MAX_DELAY_SECONDS:-2}"
readonly CLEAN_MATCHING_BACKOFF_MULTIPLIER="${CLEAN_MATCHING_BACKOFF_MULTIPLIER:-1.0}"
readonly CLEAN_E2E_CONFIG_ONLY="${CLEAN_E2E_CONFIG_ONLY:-false}"
readonly -a COMPOSE_FILES=(
  -f docker-compose.yml
  -f docker-compose.secrets.yml
  -f docker-compose.isolated-e2e.yml
)
readonly CLEAN_COMPOSE_FILE="docker-compose.yml:docker-compose.secrets.yml:docker-compose.isolated-e2e.yml"

command -v docker >/dev/null
command -v curl >/dev/null
command -v jq >/dev/null
command -v mvn >/dev/null
if [[ -n "${JAVA_HOME:-}" ]]; then
  [[ -x "$JAVA_HOME/bin/java" ]]
else
  command -v java >/dev/null
fi

if [[ ! "$RUN_ID" =~ ^[a-zA-Z0-9][a-zA-Z0-9_-]*$ ]]; then
  printf 'CLEAN_RUN_ID contains unsupported characters: %s\n' "$RUN_ID" >&2
  exit 1
fi
case "$CLEAN_E2E_CONFIG_ONLY" in
  true|false)
    ;;
  *)
    printf 'CLEAN_E2E_CONFIG_ONLY must be true or false, got %s\n' \
      "$CLEAN_E2E_CONFIG_ONLY" >&2
    exit 1
    ;;
esac
if [[ "$CLEAN_PROJECT_NAME" == "backend_delivery" ]]; then
  printf '%s\n' 'Clean E2E project must never be the canonical backend_delivery project.' >&2
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  printf '%s\n' 'Docker daemon is unavailable; clean E2E was not executed.' >&2
  exit 1
fi

clean_compose() {
  COMPOSE_PROJECT_NAME="$CLEAN_PROJECT_NAME" \
  POSTGRES_VOLUME_NAME="$CLEAN_POSTGRES_VOLUME" \
  KAFKA_VOLUME_NAME="$CLEAN_KAFKA_VOLUME" \
    docker compose "${COMPOSE_FILES[@]}" "$@"
}

if docker volume inspect "$CLEAN_POSTGRES_VOLUME" >/dev/null 2>&1 \
    || docker volume inspect "$CLEAN_KAFKA_VOLUME" >/dev/null 2>&1; then
  printf 'Refusing to reuse non-clean run volumes for %s. Choose another CLEAN_RUN_ID.\n' \
    "$RUN_ID" >&2
  exit 1
fi
if [[ -n "$(clean_compose ps -aq)" ]]; then
  printf 'Refusing to reuse existing Compose project %s. Choose another CLEAN_RUN_ID.\n' \
    "$CLEAN_PROJECT_NAME" >&2
  exit 1
fi

# The isolated overlay is a safety boundary, not merely a convenient test
# setting. Fixed names/ports would make a second project conflict with the
# canonical developer stack before any business test begins.
isolated_config="$(clean_compose config --format json)"
printf '%s' "$isolated_config" | jq -e '
  . as $root
  | ([
    "postgres", "redis", "kafka", "elasticsearch", "api-gateway",
    "auth-service", "user-service", "restaurant-service", "order-service",
    "delivery-service", "search-service", "shipper-service", "settlement-service",
    "notification-service", "match-service", "tracking-service", "livestream-service",
    "saga-orchestrator-service", "promotion-service", "analytics-service",
    "flashsale-service", "prometheus", "grafana"
  ] | all(. as $service | ($root.services[$service].container_name // null) == null))
  and (["tracing-collector", "postgres", "redis", "kafka", "elasticsearch"]
       | all(. as $service | (($root.services[$service].ports // []) | length) == 0))
  and (($root.services["api-gateway"].ports // []) | length) == 1
  and $root.services["api-gateway"].ports[0].target == 8079
  and $root.services["api-gateway"].ports[0].host_ip == "127.0.0.1"
  and ($root.services["api-gateway"].ports[0].published // null) == null
' >/dev/null || {
  printf '%s\n' 'Isolated Compose overlay still exposes a fixed container or host port.' >&2
  exit 1
}

if [[ "$CLEAN_E2E_CONFIG_ONLY" == "true" ]]; then
  printf 'Isolated clean E2E configuration is valid for project %s.\n' "$CLEAN_PROJECT_NAME"
  exit 0
fi

clean_started=false

cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM
  if [[ "$clean_started" == "true" ]]; then
    if (( exit_code != 0 )); then
      printf '%s\n' 'Clean E2E failed; capturing focused disposable-project state and logs...' >&2
      clean_compose ps -a >&2 || true
      clean_compose logs --no-color --tail=160 \
        restaurant-service order-service delivery-service saga-orchestrator-service \
        match-service notification-service >&2 || true
    fi
    clean_compose down -v --remove-orphans >/dev/null 2>&1 || true
  fi
  exit "$exit_code"
}
trap cleanup EXIT INT TERM

# Dockerfiles copy prebuilt service JARs. Package every module before starting
# the disposable project so a clean Compose build cannot accidentally use a
# stale/missing artifact. This is a build-only operation; it does not touch the
# canonical Compose project or volumes.
printf '%s\n' 'Validating build policy and packaging all service JARs before isolated E2E...'
bash scripts/verify-build-baseline.sh
mvn -q -DskipTests package

printf 'Starting isolated clean Compose project %s with fresh PostgreSQL/Kafka volumes...\n' \
  "$CLEAN_PROJECT_NAME"
clean_started=true
COMPOSE_PROJECT_NAME="$CLEAN_PROJECT_NAME" \
POSTGRES_VOLUME_NAME="$CLEAN_POSTGRES_VOLUME" \
KAFKA_VOLUME_NAME="$CLEAN_KAFKA_VOLUME" \
RUNTIME_ISOLATED=true \
RUNTIME_REBUILD_IMAGES=true \
MATCHING_INITIAL_MAX_RETRY_ATTEMPTS="$CLEAN_MATCHING_MAX_RETRY_ATTEMPTS" \
MATCHING_INITIAL_DELAY_SECONDS="$CLEAN_MATCHING_DELAY_SECONDS" \
MATCHING_INITIAL_MAX_DELAY_SECONDS="$CLEAN_MATCHING_MAX_DELAY_SECONDS" \
MATCHING_INITIAL_BACKOFF_MULTIPLIER="$CLEAN_MATCHING_BACKOFF_MULTIPLIER" \
  bash scripts/verify-runtime-startup.sh

gateway_mapping="$(clean_compose port api-gateway 8079 | head -n 1)"
gateway_port="${gateway_mapping##*:}"
if [[ ! "$gateway_port" =~ ^[0-9]+$ ]]; then
  printf 'Could not resolve disposable Gateway port from %s.\n' "$gateway_mapping" >&2
  exit 1
fi
readonly CLEAN_BASE="http://127.0.0.1:${gateway_port}"

COMPOSE_PROJECT_NAME="$CLEAN_PROJECT_NAME" \
COMPOSE_FILE="$CLEAN_COMPOSE_FILE" \
POSTGRES_VOLUME_NAME="$CLEAN_POSTGRES_VOLUME" \
KAFKA_VOLUME_NAME="$CLEAN_KAFKA_VOLUME" \
SEED_LOCAL_FIXTURE_EMAIL_VERIFIED=true \
BASE="$CLEAN_BASE" \
  bash scripts/verify-mvp-cod-flow.sh

COMPOSE_PROJECT_NAME="$CLEAN_PROJECT_NAME" \
COMPOSE_FILE="$CLEAN_COMPOSE_FILE" \
POSTGRES_VOLUME_NAME="$CLEAN_POSTGRES_VOLUME" \
KAFKA_VOLUME_NAME="$CLEAN_KAFKA_VOLUME" \
SEED_LOCAL_FIXTURE_EMAIL_VERIFIED=true \
BASE="$CLEAN_BASE" \
  bash scripts/verify-mvp-failure-matrix.sh

printf '%s\n' \
  'Clean startup + COD/notification/raw-WebSocket/settlement E2E + failure matrix passed without canonical stack downtime.'
clean_compose down -v --remove-orphans
clean_started=false

trap - EXIT INT TERM
printf 'Clean E2E run %s completed; disposable project and run-scoped volumes were removed.\n' "$RUN_ID"
