#!/usr/bin/env bash
# Show the state and health of the most recently recorded sandbox.
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
  echo "No sandbox state found." >&2
  exit 1
}
state_value() { sed -n "s/^$1=//p" "$STATE_FILE" | tail -n 1; }

readonly PROJECT_NAME="$(state_value SANDBOX_PROJECT_NAME)"
readonly NETWORK_NAME="$(state_value SANDBOX_NETWORK_NAME)"
readonly POSTGRES_VOLUME="$(state_value SANDBOX_POSTGRES_VOLUME_NAME)"
readonly KAFKA_VOLUME="$(state_value SANDBOX_KAFKA_VOLUME_NAME)"
readonly SIMULATOR_TOKEN="$(state_value SANDBOX_SIMULATOR_API_TOKEN)"
readonly -a COMPOSE_FILES=(
  -f docker-compose.yml
  -f docker-compose.secrets.yml
  -f docker-compose.isolated-e2e.yml
  -f docker-compose.simulator.yml
  -f docker-compose.sandbox.yml
)

[[ "$PROJECT_NAME" == delivery_sandbox_* && "$PROJECT_NAME" != backend_delivery ]] || {
  echo "Refusing status lookup for a non-sandbox project: $PROJECT_NAME" >&2
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

echo "Sandbox state: $STATE_FILE"
echo "Project:       $PROJECT_NAME"
echo "Gateway:       $(state_value SANDBOX_GATEWAY_BASE_URL)"
echo "Simulator:     $(state_value SANDBOX_SIMULATOR_BASE_URL)"
echo "Prometheus:    $(state_value SANDBOX_PROMETHEUS_BASE_URL)"
echo "Grafana:       $(state_value SANDBOX_GRAFANA_BASE_URL)"
echo "Batch enabled: $(state_value SANDBOX_BATCH_ENABLED)"
echo "H3 enabled:    $(state_value SANDBOX_H3_ENABLED)"
echo
docker compose "${COMPOSE_FILES[@]}" ps -a
