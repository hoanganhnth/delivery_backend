#!/usr/bin/env bash
# =============================================================================
# Start a disposable, production-like delivery sandbox.
#
# The sandbox uses the real Gateway and microservices, Kafka, Redis, PostgreSQL,
# observability and the dev/test-only Scenario Lab. Actors and money are
# synthetic. Every host binding is loopback-only and every stateful resource is
# namespaced by a run-scoped project/volume name.
#
# Usage:
#   bash scripts/sandbox-up.sh
#   SANDBOX_RUN_SCENARIO=true bash scripts/sandbox-up.sh
#
# Stop safely with:
#   bash scripts/sandbox-down.sh
# =============================================================================
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly BACKEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$BACKEND_DIR"
# Never inherit a caller's Compose file list; every command below chooses the
# sandbox files explicitly, and the seed step sets its own list temporarily.
unset COMPOSE_FILE COMPOSE_PROFILES

command -v docker >/dev/null || { echo "Docker CLI is required" >&2; exit 1; }
command -v openssl >/dev/null || { echo "openssl is required" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq is required" >&2; exit 1; }
command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }

SANDBOX_SKIP_BUILD="${SANDBOX_SKIP_BUILD:-false}"
case "$SANDBOX_SKIP_BUILD" in
  true|false) ;;
  *) echo "SANDBOX_SKIP_BUILD must be true or false" >&2; exit 2 ;;
esac

if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon is unavailable; sandbox was not started." >&2
  exit 1
fi

readonly RUN_ID="${SANDBOX_RUN_ID:-$(date +%Y%m%d%H%M%S)-$$}"
[[ "$RUN_ID" =~ ^[a-zA-Z0-9][a-zA-Z0-9_-]*$ ]] || {
  echo "SANDBOX_RUN_ID contains unsupported characters: $RUN_ID" >&2
  exit 1
}

readonly PROJECT_NAME="delivery_sandbox_${RUN_ID//-/_}"
readonly NETWORK_NAME="${SANDBOX_NETWORK_NAME:-${PROJECT_NAME}_network}"
readonly POSTGRES_VOLUME="${SANDBOX_POSTGRES_VOLUME_NAME:-${PROJECT_NAME}_postgres_data}"
readonly KAFKA_VOLUME="${SANDBOX_KAFKA_VOLUME_NAME:-${PROJECT_NAME}_kafka_data}"
readonly STATE_DIR="${SANDBOX_STATE_DIR:-$BACKEND_DIR/.sandbox/$RUN_ID}"
readonly STATE_FILE="$STATE_DIR/state.env"
readonly SECRETS_DIR="$STATE_DIR/secrets"
readonly SEED_FILE="$STATE_DIR/seed.json"
readonly HAPPY_SCENARIO="$STATE_DIR/scenario-happy.json"
readonly REJECT_SCENARIO="$STATE_DIR/scenario-restaurant-reject.json"
readonly NO_SHIPPER_SCENARIO="$STATE_DIR/scenario-no-shipper.json"
readonly HUMAN_SCENARIO="$STATE_DIR/scenario-human-order.json"

if [[ "$PROJECT_NAME" == "backend_delivery" || "$PROJECT_NAME" != delivery_sandbox_* ]]; then
  echo "Refusing to use a non-sandbox Compose project: $PROJECT_NAME" >&2
  exit 1
fi
case "$STATE_DIR" in
  "$BACKEND_DIR/.sandbox/"*) ;;
  *)
    echo "SANDBOX_STATE_DIR must remain under $BACKEND_DIR/.sandbox/" >&2
    exit 1
    ;;
esac
if [[ "$POSTGRES_VOLUME" != delivery_sandbox_* || "$KAFKA_VOLUME" != delivery_sandbox_* \
    || "$NETWORK_NAME" != delivery_sandbox_* ]]; then
  echo "Sandbox network/volume names must use the delivery_sandbox_ prefix." >&2
  exit 1
fi
if [[ -e "$STATE_DIR" ]]; then
  echo "Sandbox state already exists: $STATE_DIR (choose another SANDBOX_RUN_ID)" >&2
  exit 1
fi
if docker volume inspect "$POSTGRES_VOLUME" >/dev/null 2>&1 \
    || docker volume inspect "$KAFKA_VOLUME" >/dev/null 2>&1; then
  echo "Refusing to reuse an existing sandbox volume; choose another SANDBOX_RUN_ID." >&2
  exit 1
fi

readonly -a COMPOSE_FILES=(
  -f docker-compose.yml
  -f docker-compose.secrets.yml
  -f docker-compose.isolated-e2e.yml
  -f docker-compose.simulator.yml
  -f docker-compose.sandbox.yml
)
readonly COMPOSE_FILE_VALUE="docker-compose.yml:docker-compose.secrets.yml:docker-compose.isolated-e2e.yml:docker-compose.simulator.yml:docker-compose.sandbox.yml"

mkdir -p "$SECRETS_DIR"
chmod 700 "$STATE_DIR" "$SECRETS_DIR"
umask 077

readonly JWT_PRIVATE_KEY_FILE="$SECRETS_DIR/jwt-private.pem"
readonly JWT_PUBLIC_KEY_FILE="$SECRETS_DIR/jwt-public.pem"
readonly INTERNAL_SECRET_FILE="$SECRETS_DIR/internal-secret"
readonly DB_PASSWORD_FILE="$SECRETS_DIR/db-password"

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out "$JWT_PRIVATE_KEY_FILE" 2>/dev/null
openssl pkey -in "$JWT_PRIVATE_KEY_FILE" -pubout -out "$JWT_PUBLIC_KEY_FILE" 2>/dev/null
printf '%s\n' "$(openssl rand -hex 32)" > "$INTERNAL_SECRET_FILE"
printf '%s\n' "$(openssl rand -hex 24)" > "$DB_PASSWORD_FILE"

readonly INTERNAL_SECRET="$(tr -d '\r\n' < "$INTERNAL_SECRET_FILE")"
readonly POSTGRES_PASSWORD="$(tr -d '\r\n' < "$DB_PASSWORD_FILE")"
readonly GRAFANA_ADMIN_PASSWORD="$(openssl rand -hex 24)"
readonly SIMULATOR_API_TOKEN="$(openssl rand -hex 32)"

export COMPOSE_PROJECT_NAME="$PROJECT_NAME"
export SANDBOX_NETWORK_NAME="$NETWORK_NAME"
export POSTGRES_VOLUME_NAME="$POSTGRES_VOLUME"
export KAFKA_VOLUME_NAME="$KAFKA_VOLUME"
export JWT_PRIVATE_KEY_FILE JWT_PUBLIC_KEY_FILE INTERNAL_SECRET_FILE DB_PASSWORD_FILE
export INTERNAL_SECRET POSTGRES_PASSWORD GRAFANA_ADMIN_PASSWORD
export SANDBOX_SIMULATOR_API_TOKEN="$SIMULATOR_API_TOKEN"
export SANDBOX_BATCH_ENABLED="${SANDBOX_BATCH_ENABLED:-true}"
export SANDBOX_BATCH_SCHEDULER_ENABLED="${SANDBOX_BATCH_SCHEDULER_ENABLED:-true}"
export SANDBOX_BATCH_CLIENT_CAPABILITY_ENABLED="${SANDBOX_BATCH_CLIENT_CAPABILITY_ENABLED:-true}"
export SANDBOX_H3_ENABLED="${SANDBOX_H3_ENABLED:-true}"
export SANDBOX_BATCH_CLIENT_CAPABILITY_REQUIRED="${SANDBOX_BATCH_CLIENT_CAPABILITY_REQUIRED:-true}"
export SANDBOX_BATCH_CANARY_PERCENT="${SANDBOX_BATCH_CANARY_PERCENT:-100}"
export MATCHING_INITIAL_MAX_RETRY_ATTEMPTS="${SANDBOX_MATCHING_MAX_RETRY_ATTEMPTS:-3}"
export MATCHING_INITIAL_DELAY_SECONDS="${SANDBOX_MATCHING_INITIAL_DELAY_SECONDS:-1}"
export MATCHING_INITIAL_MAX_DELAY_SECONDS="${SANDBOX_MATCHING_INITIAL_MAX_DELAY_SECONDS:-3}"
export MATCHING_INITIAL_BACKOFF_MULTIPLIER="${SANDBOX_MATCHING_BACKOFF_MULTIPLIER:-1.0}"

for flag_name in SANDBOX_BATCH_ENABLED SANDBOX_BATCH_SCHEDULER_ENABLED \
    SANDBOX_BATCH_CLIENT_CAPABILITY_ENABLED SANDBOX_H3_ENABLED \
    SANDBOX_BATCH_CLIENT_CAPABILITY_REQUIRED; do
  flag_value="${!flag_name}"
  [[ "$flag_value" == "true" || "$flag_value" == "false" ]] || {
    echo "$flag_name must be true or false, got $flag_value" >&2
    exit 2
  }
done

cat > "$STATE_FILE" <<EOF
SANDBOX_RUN_ID=$RUN_ID
SANDBOX_PROJECT_NAME=$PROJECT_NAME
SANDBOX_NETWORK_NAME=$NETWORK_NAME
SANDBOX_POSTGRES_VOLUME_NAME=$POSTGRES_VOLUME
SANDBOX_KAFKA_VOLUME_NAME=$KAFKA_VOLUME
SANDBOX_STATE_DIR=$STATE_DIR
SANDBOX_GATEWAY_BASE_URL=
SANDBOX_SIMULATOR_BASE_URL=
SANDBOX_PROMETHEUS_BASE_URL=
SANDBOX_GRAFANA_BASE_URL=
SANDBOX_SIMULATOR_API_TOKEN=$SIMULATOR_API_TOKEN
SANDBOX_COMPOSE_FILE=$COMPOSE_FILE_VALUE
INTERNAL_SECRET_FILE=$INTERNAL_SECRET_FILE
DB_PASSWORD_FILE=$DB_PASSWORD_FILE
JWT_PRIVATE_KEY_FILE=$JWT_PRIVATE_KEY_FILE
JWT_PUBLIC_KEY_FILE=$JWT_PUBLIC_KEY_FILE
GRAFANA_ADMIN_PASSWORD=$GRAFANA_ADMIN_PASSWORD
SANDBOX_BATCH_ENABLED=$SANDBOX_BATCH_ENABLED
SANDBOX_BATCH_SCHEDULER_ENABLED=$SANDBOX_BATCH_SCHEDULER_ENABLED
SANDBOX_BATCH_CLIENT_CAPABILITY_ENABLED=$SANDBOX_BATCH_CLIENT_CAPABILITY_ENABLED
SANDBOX_H3_ENABLED=$SANDBOX_H3_ENABLED
MATCHING_INITIAL_MAX_RETRY_ATTEMPTS=$MATCHING_INITIAL_MAX_RETRY_ATTEMPTS
MATCHING_INITIAL_DELAY_SECONDS=$MATCHING_INITIAL_DELAY_SECONDS
MATCHING_INITIAL_MAX_DELAY_SECONDS=$MATCHING_INITIAL_MAX_DELAY_SECONDS
MATCHING_INITIAL_BACKOFF_MULTIPLIER=$MATCHING_INITIAL_BACKOFF_MULTIPLIER
EOF
chmod 600 "$STATE_FILE"

compose() {
  docker compose "${COMPOSE_FILES[@]}" "$@"
}

started=true
cleanup_on_error() {
  local exit_code=$?
  trap - EXIT INT TERM
  if [[ "$started" == "true" && "$exit_code" -ne 0 ]]; then
    echo "Sandbox startup failed; capturing disposable state before cleanup..." >&2
    compose ps -a >&2 || true
    compose logs --no-color --tail=120 simulator-service match-service delivery-service saga-orchestrator-service >&2 || true
    compose down -v --remove-orphans >/dev/null 2>&1 || true
    echo "Partial sandbox state retained for diagnosis: $STATE_DIR" >&2
  fi
  exit "$exit_code"
}
trap cleanup_on_error EXIT INT TERM

echo "Validating sandbox Compose boundary for project $PROJECT_NAME..."
compose config --quiet

if [[ "$SANDBOX_SKIP_BUILD" != "true" ]]; then
  echo "Packaging fresh artifacts for the production-like service set..."
  bash scripts/package-compose-services.sh \
    config-server discovery-server auth-service user-service api-gateway \
    restaurant-service order-service delivery-service search-service \
    shipper-service settlement-service notification-service match-service \
    tracking-service routing-service saga-orchestrator-service simulator-service
else
  echo "SANDBOX_SKIP_BUILD=true — using existing verified Compose artifacts."
fi

echo "Starting isolated runtime (Gateway -> services -> Kafka/Saga -> Match -> Delivery -> Tracking)..."
runtime_rebuild_images=true
if [[ "$SANDBOX_SKIP_BUILD" == "true" ]]; then runtime_rebuild_images=false; fi
RUNTIME_ISOLATED=true \
RUNTIME_REBUILD_IMAGES="$runtime_rebuild_images" \
RUNTIME_EXTRA_COMPOSE_FILES="docker-compose.simulator.yml:docker-compose.sandbox.yml" \
RUNTIME_INCLUDE_SIMULATOR=true \
STARTUP_TIMEOUT_SECONDS="${SANDBOX_STARTUP_TIMEOUT_SECONDS:-900}" \
EUREKA_REGISTRATION_TIMEOUT_SECONDS="${SANDBOX_EUREKA_TIMEOUT_SECONDS:-180}" \
  bash scripts/verify-runtime-startup.sh

gateway_mapping="$(compose port api-gateway 8079 | head -n 1)"
gateway_port="${gateway_mapping##*:}"
simulator_mapping="$(compose port simulator-service 8100 | head -n 1)"
simulator_port="${simulator_mapping##*:}"
prometheus_mapping="$(compose port prometheus 9090 | head -n 1)"
prometheus_port="${prometheus_mapping##*:}"
grafana_mapping="$(compose port grafana 3000 | head -n 1)"
grafana_port="${grafana_mapping##*:}"
[[ "$gateway_port" =~ ^[0-9]+$ ]] || { echo "Cannot resolve Gateway port: $gateway_mapping" >&2; exit 1; }
[[ "$simulator_port" =~ ^[0-9]+$ ]] || { echo "Cannot resolve simulator port: $simulator_mapping" >&2; exit 1; }
[[ "$prometheus_port" =~ ^[0-9]+$ ]] || { echo "Cannot resolve Prometheus port: $prometheus_mapping" >&2; exit 1; }
[[ "$grafana_port" =~ ^[0-9]+$ ]] || { echo "Cannot resolve Grafana port: $grafana_mapping" >&2; exit 1; }
readonly GATEWAY_BASE_URL="http://127.0.0.1:$gateway_port"
readonly SIMULATOR_BASE_URL="http://127.0.0.1:$simulator_port"
readonly PROMETHEUS_BASE_URL="http://127.0.0.1:$prometheus_port"
readonly GRAFANA_BASE_URL="http://127.0.0.1:$grafana_port"

echo "Seeding synthetic customer, restaurant, menu and shipper through Gateway..."
COMPOSE_FILE="$COMPOSE_FILE_VALUE" \
COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
POSTGRES_VOLUME_NAME="$POSTGRES_VOLUME" \
KAFKA_VOLUME_NAME="$KAFKA_VOLUME" \
SEED_LOCAL_FIXTURE_EMAIL_VERIFIED=true \
SEED_OUTPUT_FILE="$SEED_FILE" \
RUN_ID="$RUN_ID" \
BASE="$GATEWAY_BASE_URL" \
  bash scripts/seed.sh

bash scripts/sandbox-make-scenario.sh "$SEED_FILE" "$HAPPY_SCENARIO" happy
bash scripts/sandbox-make-scenario.sh "$SEED_FILE" "$REJECT_SCENARIO" restaurant-reject
bash scripts/sandbox-make-scenario.sh "$SEED_FILE" "$NO_SHIPPER_SCENARIO" no-shipper
bash scripts/sandbox-make-scenario.sh "$SEED_FILE" "$HUMAN_SCENARIO" human-order

# Complete the state file only after the Gateway and simulator ports have been
# resolved. The token remains on disk with mode 0600 and is never printed.
sed -i.bak \
  -e "s#^SANDBOX_GATEWAY_BASE_URL=.*#SANDBOX_GATEWAY_BASE_URL=$GATEWAY_BASE_URL#" \
  -e "s#^SANDBOX_SIMULATOR_BASE_URL=.*#SANDBOX_SIMULATOR_BASE_URL=$SIMULATOR_BASE_URL#" \
  -e "s#^SANDBOX_PROMETHEUS_BASE_URL=.*#SANDBOX_PROMETHEUS_BASE_URL=$PROMETHEUS_BASE_URL#" \
  -e "s#^SANDBOX_GRAFANA_BASE_URL=.*#SANDBOX_GRAFANA_BASE_URL=$GRAFANA_BASE_URL#" \
  "$STATE_FILE"
rm -f "$STATE_FILE.bak"
chmod 600 "$STATE_FILE" "$SEED_FILE" "$HAPPY_SCENARIO" "$REJECT_SCENARIO" "$NO_SHIPPER_SCENARIO" "$HUMAN_SCENARIO"

started=false
trap - EXIT INT TERM

cat <<EOF

Sandbox is ready (synthetic data only).
  project:   $PROJECT_NAME
  Gateway:   $GATEWAY_BASE_URL
  Simulator: $SIMULATOR_BASE_URL
  Prometheus: $PROMETHEUS_BASE_URL
  Grafana:   $GRAFANA_BASE_URL (admin password is in run state only)
  state:     $STATE_DIR

Run scenarios without handling actor tokens:
  bash scripts/sandbox-run.sh happy
  bash scripts/sandbox-run.sh restaurant-reject
  bash scripts/sandbox-run.sh no-shipper
  bash scripts/sandbox-run.sh human-order   # wait for a real Delivery App order

Inspect/cleanup:
  bash scripts/sandbox-status.sh
  bash scripts/sandbox-down.sh             # stop, retain volumes
  SANDBOX_PURGE=true bash scripts/sandbox-down.sh  # delete this sandbox only

For the visual console, run delivery_simulator_web in backend mode and point
VITE_SIMULATOR_API_BASE_URL at $SIMULATOR_BASE_URL/api/simulator.
EOF

if [[ "${SANDBOX_RUN_SCENARIO:-false}" == "true" ]]; then
  bash scripts/sandbox-run.sh happy
fi
