#!/usr/bin/env bash
set -euo pipefail

# Runs the full stack against run-scoped volumes while preserving the canonical
# volumes. Explicit container_name entries require temporary canonical downtime.
if [[ "${ALLOW_CANONICAL_DOWNTIME:-false}" != "true" ]]; then
  printf '%s\n' \
    "Set ALLOW_CANONICAL_DOWNTIME=true to authorize temporary container downtime." >&2
  exit 1
fi

readonly CANONICAL_PROJECT_NAME="${CANONICAL_PROJECT_NAME:-backend_delivery}"
readonly RUN_ID="${CLEAN_RUN_ID:-$(date +%Y%m%d%H%M%S)-$$}"
readonly CLEAN_PROJECT_NAME="delivery_b8_${RUN_ID//-/_}"
canonical_postgres_volume_override="${CANONICAL_POSTGRES_VOLUME:-${POSTGRES_VOLUME_NAME:-}}"
canonical_kafka_volume_override="${CANONICAL_KAFKA_VOLUME:-${KAFKA_VOLUME_NAME:-}}"
canonical_postgres_host_port_override="${CANONICAL_POSTGRES_HOST_PORT:-${POSTGRES_HOST_PORT:-}}"
readonly CLEAN_POSTGRES_VOLUME="backend_delivery_b8_${RUN_ID}_postgres_data"
readonly CLEAN_KAFKA_VOLUME="backend_delivery_b8_${RUN_ID}_kafka_data"
readonly CLEAN_MATCHING_MAX_RETRY_ATTEMPTS="${CLEAN_MATCHING_MAX_RETRY_ATTEMPTS:-2}"
readonly CLEAN_MATCHING_DELAY_SECONDS="${CLEAN_MATCHING_DELAY_SECONDS:-1}"
readonly CLEAN_MATCHING_MAX_DELAY_SECONDS="${CLEAN_MATCHING_MAX_DELAY_SECONDS:-2}"
readonly CLEAN_MATCHING_BACKOFF_MULTIPLIER="${CLEAN_MATCHING_BACKOFF_MULTIPLIER:-1.0}"
readonly -a COMPOSE_FILES=(-f docker-compose.yml -f docker-compose.secrets.yml)

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

if ! docker info >/dev/null 2>&1; then
  printf '%s\n' "Docker daemon is unavailable; clean E2E was not executed." >&2
  exit 1
fi

existing_postgres_project="$(docker inspect delivery-postgres \
  --format '{{index .Config.Labels "com.docker.compose.project"}}' 2>/dev/null || true)"
if [[ -n "$existing_postgres_project" \
    && "$existing_postgres_project" != "$CANONICAL_PROJECT_NAME" ]]; then
  printf 'Container delivery-postgres belongs to unexpected project %s; refusing downtime.\n' \
    "$existing_postgres_project" >&2
  exit 1
fi

# Preserve the exact canonical runtime that is mounted now. A manually selected
# rehearsal volume/host port may differ from Compose's generated default name;
# guessing here can restore an older schema after downtime.
detected_postgres_volume=""
detected_kafka_volume=""
detected_postgres_host_port=""
if [[ -n "$existing_postgres_project" ]]; then
  existing_postgres_running="$(docker inspect delivery-postgres \
    --format '{{.State.Running}}')"
  detected_postgres_volume="$(docker inspect delivery-postgres --format \
    '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}')"
  if [[ "$existing_postgres_running" == "true" ]]; then
    detected_postgres_host_port="$(docker inspect delivery-postgres --format \
      '{{(index (index .NetworkSettings.Ports "5432/tcp") 0).HostPort}}')"
  fi
  detected_kafka_volume="$(docker inspect delivery-kafka --format \
    '{{range .Mounts}}{{if eq .Destination "/var/lib/kafka/data"}}{{.Name}}{{end}}{{end}}')"
fi

if [[ -n "$canonical_postgres_volume_override" \
    && -n "$detected_postgres_volume" \
    && "$canonical_postgres_volume_override" != "$detected_postgres_volume" ]]; then
  printf 'Configured canonical PostgreSQL volume %s does not match mounted volume %s.\n' \
    "$canonical_postgres_volume_override" "$detected_postgres_volume" >&2
  exit 1
fi
if [[ -n "$canonical_kafka_volume_override" \
    && -n "$detected_kafka_volume" \
    && "$canonical_kafka_volume_override" != "$detected_kafka_volume" ]]; then
  printf 'Configured canonical Kafka volume %s does not match mounted volume %s.\n' \
    "$canonical_kafka_volume_override" "$detected_kafka_volume" >&2
  exit 1
fi
if [[ -n "$canonical_postgres_host_port_override" \
    && -n "$detected_postgres_host_port" \
    && "$canonical_postgres_host_port_override" != "$detected_postgres_host_port" ]]; then
  printf 'Configured canonical PostgreSQL host port %s does not match published port %s.\n' \
    "$canonical_postgres_host_port_override" "$detected_postgres_host_port" >&2
  exit 1
fi

readonly CANONICAL_POSTGRES_VOLUME="${canonical_postgres_volume_override:-${detected_postgres_volume:-backend_delivery_postgres_data}}"
readonly CANONICAL_KAFKA_VOLUME="${canonical_kafka_volume_override:-${detected_kafka_volume:-backend_delivery_kafka_data}}"
readonly CANONICAL_POSTGRES_HOST_PORT="${canonical_postgres_host_port_override:-${detected_postgres_host_port:-15432}}"
readonly CLEAN_POSTGRES_HOST_PORT="${CLEAN_POSTGRES_HOST_PORT:-$CANONICAL_POSTGRES_HOST_PORT}"

canonical_compose() {
  COMPOSE_PROJECT_NAME="$CANONICAL_PROJECT_NAME" \
  POSTGRES_VOLUME_NAME="$CANONICAL_POSTGRES_VOLUME" \
  KAFKA_VOLUME_NAME="$CANONICAL_KAFKA_VOLUME" \
  POSTGRES_HOST_PORT="$CANONICAL_POSTGRES_HOST_PORT" \
    docker compose "${COMPOSE_FILES[@]}" "$@"
}

clean_compose() {
  COMPOSE_PROJECT_NAME="$CLEAN_PROJECT_NAME" \
  POSTGRES_VOLUME_NAME="$CLEAN_POSTGRES_VOLUME" \
  KAFKA_VOLUME_NAME="$CLEAN_KAFKA_VOLUME" \
  POSTGRES_HOST_PORT="$CLEAN_POSTGRES_HOST_PORT" \
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

canonical_was_running=false
canonical_stopped=false
clean_started=false
restored=false

if [[ -n "$(canonical_compose ps -q)" ]]; then
  canonical_was_running=true
fi

# Dockerfiles copy prebuilt service JARs. Package every module before downtime so
# a clean Compose build cannot fail because a previous `mvn clean test` removed one.
printf '%s\n' "Validating build policy and packaging all service JARs before downtime..."
bash scripts/verify-build-baseline.sh
mvn -q -DskipTests package

restore_canonical() {
  local exit_code=$?
  trap - EXIT INT TERM

  if [[ "$clean_started" == "true" ]]; then
    if (( exit_code != 0 )); then
      printf '%s\n' "Clean E2E failed; capturing focused container state and logs..." >&2
      clean_compose ps -a >&2 || true
      clean_compose logs --no-color --tail=160 \
        restaurant-service order-service delivery-service \
        saga-orchestrator-service match-service notification-service >&2 || true
    fi
    clean_compose down -v --remove-orphans >/dev/null 2>&1 || true
  fi
  if [[ "$canonical_was_running" == "true" \
      && "$canonical_stopped" == "true" \
      && "$restored" != "true" ]]; then
    printf '%s\n' "Restoring canonical Compose containers with existing volumes..." >&2
    canonical_compose up -d >/dev/null || true
  fi
  exit "$exit_code"
}
trap restore_canonical EXIT INT TERM

if [[ "$canonical_was_running" == "true" ]]; then
  printf '%s\n' "Stopping canonical containers without deleting volumes..."
  canonical_stopped=true
  canonical_compose down --remove-orphans
fi

printf 'Starting clean Compose project %s with run-scoped PostgreSQL/Kafka volumes...\n' \
  "$CLEAN_PROJECT_NAME"
clean_started=true
COMPOSE_PROJECT_NAME="$CLEAN_PROJECT_NAME" \
POSTGRES_VOLUME_NAME="$CLEAN_POSTGRES_VOLUME" \
KAFKA_VOLUME_NAME="$CLEAN_KAFKA_VOLUME" \
POSTGRES_HOST_PORT="$CLEAN_POSTGRES_HOST_PORT" \
MATCHING_INITIAL_MAX_RETRY_ATTEMPTS="$CLEAN_MATCHING_MAX_RETRY_ATTEMPTS" \
MATCHING_INITIAL_DELAY_SECONDS="$CLEAN_MATCHING_DELAY_SECONDS" \
MATCHING_INITIAL_MAX_DELAY_SECONDS="$CLEAN_MATCHING_MAX_DELAY_SECONDS" \
MATCHING_INITIAL_BACKOFF_MULTIPLIER="$CLEAN_MATCHING_BACKOFF_MULTIPLIER" \
  bash scripts/verify-runtime-startup.sh

COMPOSE_PROJECT_NAME="$CLEAN_PROJECT_NAME" \
POSTGRES_VOLUME_NAME="$CLEAN_POSTGRES_VOLUME" \
KAFKA_VOLUME_NAME="$CLEAN_KAFKA_VOLUME" \
POSTGRES_HOST_PORT="$CLEAN_POSTGRES_HOST_PORT" \
  bash scripts/verify-mvp-cod-flow.sh

COMPOSE_PROJECT_NAME="$CLEAN_PROJECT_NAME" \
POSTGRES_VOLUME_NAME="$CLEAN_POSTGRES_VOLUME" \
KAFKA_VOLUME_NAME="$CLEAN_KAFKA_VOLUME" \
POSTGRES_HOST_PORT="$CLEAN_POSTGRES_HOST_PORT" \
  bash scripts/verify-mvp-failure-matrix.sh

printf '%s\n' \
  "Clean startup + COD/notification/raw-WebSocket/settlement E2E + failure matrix passed."
clean_compose down -v --remove-orphans
clean_started=false

if [[ "$canonical_was_running" == "true" ]]; then
  printf '%s\n' "Restoring and validating canonical Compose project..."
  canonical_compose up -d
  canonical_stopped=false
  restored=true
  COMPOSE_PROJECT_NAME="$CANONICAL_PROJECT_NAME" \
  POSTGRES_VOLUME_NAME="$CANONICAL_POSTGRES_VOLUME" \
  KAFKA_VOLUME_NAME="$CANONICAL_KAFKA_VOLUME" \
  POSTGRES_HOST_PORT="$CANONICAL_POSTGRES_HOST_PORT" \
    bash scripts/verify-runtime-startup.sh
fi

trap - EXIT INT TERM
printf 'Clean E2E run %s completed; run-scoped volumes were removed.\n' "$RUN_ID"
