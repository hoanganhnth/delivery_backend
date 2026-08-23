#!/usr/bin/env bash
# Stop only the run-scoped sandbox recorded by sandbox-up.sh.
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly BACKEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$BACKEND_DIR"
unset COMPOSE_FILE COMPOSE_PROFILES

resolve_state_file() {
  if [[ -n "${SANDBOX_STATE_DIR:-}" && -f "$SANDBOX_STATE_DIR/state.env" ]]; then
    printf '%s\n' "$SANDBOX_STATE_DIR/state.env"
    return
  fi
  if [[ -n "${SANDBOX_RUN_ID:-}" && -f "$BACKEND_DIR/.sandbox/$SANDBOX_RUN_ID/state.env" ]]; then
    printf '%s\n' "$BACKEND_DIR/.sandbox/$SANDBOX_RUN_ID/state.env"
    return
  fi
  find "$BACKEND_DIR/.sandbox" -mindepth 2 -maxdepth 2 -type f -name state.env \
    -print 2>/dev/null | LC_ALL=C sort | tail -n 1
}

readonly STATE_FILE="$(resolve_state_file)"
[[ -f "$STATE_FILE" ]] || {
  echo "No sandbox state found. Nothing was stopped." >&2
  exit 1
}
state_value() { sed -n "s/^$1=//p" "$STATE_FILE" | tail -n 1; }

readonly PROJECT_NAME="$(state_value SANDBOX_PROJECT_NAME)"
readonly NETWORK_NAME="$(state_value SANDBOX_NETWORK_NAME)"
readonly POSTGRES_VOLUME="$(state_value SANDBOX_POSTGRES_VOLUME_NAME)"
readonly KAFKA_VOLUME="$(state_value SANDBOX_KAFKA_VOLUME_NAME)"
readonly STATE_DIR="$(state_value SANDBOX_STATE_DIR)"
readonly SIMULATOR_TOKEN="$(state_value SANDBOX_SIMULATOR_API_TOKEN)"

[[ "$PROJECT_NAME" == delivery_sandbox_* && "$PROJECT_NAME" != backend_delivery ]] || {
  echo "Refusing cleanup for a non-sandbox project: $PROJECT_NAME" >&2
  exit 1
}
[[ "$POSTGRES_VOLUME" == delivery_sandbox_* && "$KAFKA_VOLUME" == delivery_sandbox_* ]] || {
  echo "Refusing cleanup for unscoped volumes." >&2
  exit 1
}

export COMPOSE_PROJECT_NAME="$PROJECT_NAME"
export SANDBOX_NETWORK_NAME="$NETWORK_NAME"
export POSTGRES_VOLUME_NAME="$POSTGRES_VOLUME"
export KAFKA_VOLUME_NAME="$KAFKA_VOLUME"
export SANDBOX_SIMULATOR_API_TOKEN="$SIMULATOR_TOKEN"
export INTERNAL_SECRET_FILE="$(state_value INTERNAL_SECRET_FILE)"
export DB_PASSWORD_FILE="$(state_value DB_PASSWORD_FILE)"
export JWT_PRIVATE_KEY_FILE="$(state_value JWT_PRIVATE_KEY_FILE)"
export JWT_PUBLIC_KEY_FILE="$(state_value JWT_PUBLIC_KEY_FILE)"
export GRAFANA_ADMIN_PASSWORD="$(state_value GRAFANA_ADMIN_PASSWORD)"

readonly -a COMPOSE_FILES=(
  -f docker-compose.yml
  -f docker-compose.secrets.yml
  -f docker-compose.isolated-e2e.yml
  -f docker-compose.simulator.yml
  -f docker-compose.sandbox.yml
)

purge="${SANDBOX_PURGE:-false}"
case "$purge" in
  true|false) ;;
  *) echo "SANDBOX_PURGE must be true or false" >&2; exit 2 ;;
esac

if [[ "$purge" == "true" ]]; then
  echo "Purging only sandbox project $PROJECT_NAME and its run-scoped volumes..."
  docker compose "${COMPOSE_FILES[@]}" down -v --remove-orphans
else
  echo "Stopping sandbox project $PROJECT_NAME (volumes retained)..."
  docker compose "${COMPOSE_FILES[@]}" down --remove-orphans
fi

if [[ "${SANDBOX_DELETE_STATE:-false}" == "true" ]]; then
  [[ "$STATE_DIR" == "$BACKEND_DIR/.sandbox/"* ]] || {
    echo "Refusing to delete state outside backend .sandbox/: $STATE_DIR" >&2
    exit 1
  }
  rm -rf "$STATE_DIR"
  echo "Deleted run-scoped state: $STATE_DIR"
else
  echo "Kept run-scoped state: $STATE_DIR (set SANDBOX_DELETE_STATE=true to remove it)"
fi
