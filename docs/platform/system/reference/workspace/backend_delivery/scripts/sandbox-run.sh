#!/usr/bin/env bash
# Run a Scenario Lab scenario against the currently recorded sandbox.
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly BACKEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$BACKEND_DIR"
unset COMPOSE_FILE COMPOSE_PROFILES

kind="${1:-happy}"
cleanup="${SANDBOX_SCENARIO_CLEANUP:-false}"
if [[ "${2:-}" == "--cleanup" ]]; then cleanup=true; fi

case "$kind" in
  happy) scenario_name="scenario-happy.json" ;;
  restaurant-reject|reject) scenario_name="scenario-restaurant-reject.json" ;;
  no-shipper|none) scenario_name="scenario-no-shipper.json" ;;
  shipper-reject) scenario_name="scenario-shipper-reject.json" ;;
  offer-timeout) scenario_name="scenario-offer-timeout.json" ;;
  customer-cancel) scenario_name="scenario-customer-cancel.json" ;;
  customer-cancel-after-accept) scenario_name="scenario-customer-cancel-after-accept.json" ;;
  shipper-disconnect) scenario_name="scenario-shipper-disconnect.json" ;;
  network-delay) scenario_name="scenario-network-delay.json" ;;
  human-order|human) scenario_name="scenario-human-order.json" ;;
  *)
    echo "Usage: sandbox-run.sh [happy|restaurant-reject|no-shipper|shipper-reject|offer-timeout|customer-cancel|customer-cancel-after-accept|shipper-disconnect|network-delay|human-order] [--cleanup]" >&2
    exit 2
    ;;
esac

resolve_state_file() {
  if [[ -n "${SANDBOX_STATE_DIR:-}" && -f "$SANDBOX_STATE_DIR/state.env" ]]; then
    printf '%s\n' "$SANDBOX_STATE_DIR/state.env"
    return
  fi
  if [[ -n "${SANDBOX_RUN_ID:-}" && -f "$BACKEND_DIR/.sandbox/$SANDBOX_RUN_ID/state.env" ]]; then
    printf '%s\n' "$BACKEND_DIR/.sandbox/$SANDBOX_RUN_ID/state.env"
    return
  fi
  local candidate
  candidate="$(find "$BACKEND_DIR/.sandbox" -mindepth 2 -maxdepth 2 -type f -name state.env \
    -print 2>/dev/null | LC_ALL=C sort | tail -n 1 || true)"
  [[ -n "$candidate" ]] || {
    echo "No sandbox state found. Start one with scripts/sandbox-up.sh." >&2
    exit 1
  }
  printf '%s\n' "$candidate"
}

readonly STATE_FILE="$(resolve_state_file)"
state_value() {
  sed -n "s/^$1=//p" "$STATE_FILE" | tail -n 1
}

readonly PROJECT_NAME="$(state_value SANDBOX_PROJECT_NAME)"
readonly NETWORK_NAME="$(state_value SANDBOX_NETWORK_NAME)"
readonly POSTGRES_VOLUME="$(state_value SANDBOX_POSTGRES_VOLUME_NAME)"
readonly KAFKA_VOLUME="$(state_value SANDBOX_KAFKA_VOLUME_NAME)"
readonly STATE_DIR="$(state_value SANDBOX_STATE_DIR)"
readonly SIMULATOR_TOKEN="$(state_value SANDBOX_SIMULATOR_API_TOKEN)"
# New sandboxes persist a run-scoped ADMIN JWT.  A caller may supply one for a
# retained legacy sandbox that predates this field; endpoint RBAC is unchanged.
readonly SIMULATOR_ADMIN_TOKEN="${SIMULATOR_ADMIN_TOKEN:-$(state_value SANDBOX_SIMULATOR_ADMIN_TOKEN)}"

[[ "$PROJECT_NAME" == delivery_sandbox_* && "$PROJECT_NAME" != backend_delivery ]] || {
  echo "State file does not describe a safe sandbox project." >&2
  exit 1
}
[[ -n "$SIMULATOR_TOKEN" ]] || {
  echo "Sandbox state is missing its simulator API token; rerun scripts/sandbox-up.sh." >&2
  exit 1
}
[[ -n "$SIMULATOR_ADMIN_TOKEN" ]] || {
  echo "Sandbox state is missing its simulator ADMIN token; rerun scripts/sandbox-up.sh." >&2
  exit 1
}

readonly SCENARIO_FILE="$STATE_DIR/$scenario_name"
[[ -f "$SCENARIO_FILE" ]] || { echo "Scenario file not found: $SCENARIO_FILE" >&2; exit 1; }

export COMPOSE_PROJECT_NAME="$PROJECT_NAME"
export SANDBOX_NETWORK_NAME="$NETWORK_NAME"
export POSTGRES_VOLUME_NAME="$POSTGRES_VOLUME"
export KAFKA_VOLUME_NAME="$KAFKA_VOLUME"
export SANDBOX_SIMULATOR_API_TOKEN="$SIMULATOR_TOKEN"
export SIMULATOR_ADMIN_TOKEN
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

if [[ "${SANDBOX_SKIP_RUNTIME_CHECK:-false}" != "true" ]]; then
  docker compose "${COMPOSE_FILES[@]}" ps simulator-service >/dev/null
fi

SIMULATOR_BASE="$(state_value SANDBOX_SIMULATOR_BASE_URL)"
if [[ -z "$SIMULATOR_BASE" ]]; then
  simulator_mapping="$(docker compose "${COMPOSE_FILES[@]}" port simulator-service 8100 | head -n 1)"
  simulator_port="${simulator_mapping##*:}"
  [[ "$simulator_port" =~ ^[0-9]+$ ]] || {
    echo "Sandbox state is missing simulator URL and Docker did not expose simulator-service:8100." >&2
    exit 1
  }
  SIMULATOR_BASE="http://127.0.0.1:$simulator_port"
  echo "Recovered simulator URL from the running sandbox port mapping."
fi

echo "Running $kind scenario through $SIMULATOR_BASE (tokens stay in the local scenario file)."
SIMULATOR_API_BASE="$SIMULATOR_BASE/api/simulator" \
SIMULATOR_API_TOKEN="$SIMULATOR_TOKEN" \
SIMULATOR_CLEANUP="$cleanup" \
  bash scripts/scenario-lab-runner.sh "$SCENARIO_FILE"
