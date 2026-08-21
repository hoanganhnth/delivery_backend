#!/usr/bin/env bash
set -euo pipefail

# Operator-owned live proof for the identity/principal event path. This script
# only attaches to an already-running Compose project. It never starts, builds,
# stops, recreates, or deletes containers/volumes. It creates one retained USER
# fixture and needs an existing ADMIN access-token file to prove lifecycle
# propagation. Do not use it against production data.

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly BASE="${IDENTITY_E2E_BASE_URL:-http://127.0.0.1:8079}"
readonly PROJECT="${COMPOSE_PROJECT_NAME:-backend_delivery}"
readonly TIMEOUT_SECONDS="${IDENTITY_E2E_TIMEOUT_SECONDS:-90}"
readonly POLL_SECONDS="${IDENTITY_E2E_POLL_SECONDS:-2}"
readonly EMAIL="${IDENTITY_E2E_EMAIL:-identity-e2e-$(date +%s)-$$@test.invalid}"
readonly PASSWORD="${IDENTITY_E2E_PASSWORD:-}"
readonly ADMIN_TOKEN_FILE="${IDENTITY_E2E_ADMIN_ACCESS_TOKEN_FILE:-}"
readonly SHIPPER_PRINCIPAL_ID="${IDENTITY_E2E_SHIPPER_PRINCIPAL_ID:-}"
readonly -a COMPOSE=(docker compose -f "${ROOT_DIR}/docker-compose.yml" -f "${ROOT_DIR}/docker-compose.secrets.yml")

die() { printf 'Identity Compose proof: %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<'EOF'
Usage (final operator-owned gate only):
  COMPOSE_PROJECT_NAME=backend_delivery \
  IDENTITY_E2E_PASSWORD='a-unique-strong-password' \
  IDENTITY_E2E_ADMIN_ACCESS_TOKEN_FILE=/secure/admin.access.jwt \
  IDENTITY_E2E_SHIPPER_PRINCIPAL_ID=123 \
  bash scripts/verify-identity-principal-compose.sh

Optional:
  IDENTITY_E2E_BASE_URL=http://127.0.0.1:8079
  IDENTITY_E2E_EMAIL=unique-address@example.invalid
  IDENTITY_E2E_TIMEOUT_SECONDS=90

Required running services: api-gateway, auth-service, user-service,
shipper-service, postgres, and kafka. The stack must have the coherent Compose
identity defaults enabled: Auth/User event consumers and outbox relays true;
public registration 100%.
IDENTITY_E2E_SHIPPER_PRINCIPAL_ID must name an existing *disposable*, active
Shipper profile whose principal_id is already populated. This proof blocks and
unblocks it; use an offline fixture to avoid a Tracking availability mutation.

Proof performed:
  1. Auth registration -> signed provisioning token -> User registration.
  2. User outbox published -> Auth inbox receipt -> Auth legacy profile link.
  3. Exact profile event replay stays idempotent (one Auth receipt/link).
  4. Auth ADMIN block/unblock projects to both User and Shipper through Kafka
     and publishes all identity records for these disposable fixtures.
  5. Identity DLT topics contain no records at the end of the proof.

The script does not print JWTs, passwords, provisioning tokens, Kafka payloads,
or SQL result rows beyond opaque IDs/statuses needed for a failure diagnosis.
EOF
}

compose() { COMPOSE_PROJECT_NAME="$PROJECT" "${COMPOSE[@]}" "$@"; }

require_command() { command -v "$1" >/dev/null || die "Required command unavailable: $1"; }

ensure_running() {
  local service="$1" id running
  id="$(compose ps -q "$service")"
  [[ -n "$id" ]] || die "Required Compose service is not running: $service"
  running="$(docker inspect --format '{{.State.Running}}' "$id")"
  [[ "$running" == true ]] || die "Required Compose service is not running: $service"
}

sql() {
  local database="$1" statement="$2"
  compose exec -T postgres psql -U postgres -d "$database" -qAt -v ON_ERROR_STOP=1 -c "$statement"
}

wait_until() {
  local description="$1" command="$2" deadline=$((SECONDS + TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    if eval "$command" >/dev/null 2>&1; then return 0; fi
    sleep "$POLL_SECONDS"
  done
  die "Timed out waiting for ${description}."
}

read_token() {
  [[ -n "$ADMIN_TOKEN_FILE" && -r "$ADMIN_TOKEN_FILE" ]] \
    || die 'Set IDENTITY_E2E_ADMIN_ACCESS_TOKEN_FILE to a readable existing ADMIN JWT file.'
  local token
  token="$(tr -d '\r\n' < "$ADMIN_TOKEN_FILE")"
  [[ "$token" == *.*.* && "$token" != *[[:space:]]* ]] \
    || die 'IDENTITY_E2E_ADMIN_ACCESS_TOKEN_FILE must contain one compact JWT.'
  printf '%s' "$token"
}

new_curl_config() {
  local token="$1"
  CURL_CONFIG="$(mktemp "${TMPDIR:-/tmp}/delivery-identity-e2e-curl.XXXXXX")"
  chmod 600 "$CURL_CONFIG"
  printf 'header = "Authorization: Bearer %s"\n' "$token" > "$CURL_CONFIG"
}

CURL_CONFIG=''
cleanup() {
  local result=$?
  [[ -z "$CURL_CONFIG" ]] || rm -f "$CURL_CONFIG"
  exit "$result"
}
trap cleanup EXIT INT TERM

auth_row() {
  sql auth_db "SELECT id || ':' || COALESCE(user_id::text, '') || ':' || lifecycle_status || ':' || lifecycle_version || ':' || CASE WHEN is_active THEN 'true' ELSE 'false' END FROM auth_account WHERE email = '${EMAIL}';"
}

user_row() {
  local principal="$1"
  sql user_db "SELECT id || ':' || principal_id || ':' || identity_status || ':' || identity_status_version || ':' || CASE WHEN is_blocked THEN 'true' ELSE 'false' END || ':' || CASE WHEN is_active THEN 'true' ELSE 'false' END FROM users WHERE principal_id = ${principal};"
}

assert_fixture_outboxes_published() {
  local principal="$1"
  [[ "$(sql user_db "SELECT count(*) FROM identity_outbox_events WHERE aggregate_id = ${principal} AND published_at IS NULL;")" == 0 ]] \
    || return 1
  [[ "$(sql auth_db "SELECT count(*) FROM identity_outbox_events WHERE aggregate_id = ${principal} AND published_at IS NULL;")" == 0 ]] \
    || return 1
}

assert_auth_outbox_published() {
  local principal="$1"
  [[ "$(sql auth_db "SELECT count(*) FROM identity_outbox_events WHERE aggregate_id = ${principal} AND published_at IS NULL;")" == 0 ]]
}

shipper_row() {
  local principal="$1"
  sql shipper_db "SELECT id || ':' || principal_id || ':' || identity_status || ':' || identity_status_version || ':' || CASE WHEN is_online THEN 'true' ELSE 'false' END FROM shipper WHERE principal_id = ${principal};"
}

assert_identity_dlt_empty() {
  local topics topic messages
  topics="$(compose exec -T kafka kafka-topics --bootstrap-server kafka:9092 --list | rg '(identity|shipper).*\.DLT$' || true)"
  while IFS= read -r topic; do
    [[ -n "$topic" ]] || continue
    messages="$(compose exec -T kafka kafka-console-consumer --bootstrap-server kafka:9092 \
      --topic "$topic" --from-beginning --timeout-ms 1000 --max-messages 1 2>/dev/null || true)"
    [[ -z "$messages" ]] || die "Identity DLT contains a record: ${topic}. Inspect/replay it before promotion."
  done <<< "$topics"
}

replay_profile_event() {
  local payload="$1"
  # Payload carries only event/profile identifiers. Keep it off stdout and pipe
  # directly to Kafka so a duplicate takes the same consumer path as replay.
  printf '%s\n' "$payload" | compose exec -T kafka kafka-console-producer \
    --bootstrap-server kafka:9092 --topic identity.profile.created >/dev/null
}

main() {
  [[ "${IDENTITY_LIVE_E2E:-false}" == true ]] || die \
    'This is the final live gate. Set IDENTITY_LIVE_E2E=true explicitly.'
  require_command docker; require_command curl; require_command jq; require_command rg; require_command mktemp
  [[ "$TIMEOUT_SECONDS" =~ ^[0-9]+$ && "$TIMEOUT_SECONDS" -gt 0 ]] \
    || die 'IDENTITY_E2E_TIMEOUT_SECONDS must be a positive integer.'
  [[ "$POLL_SECONDS" =~ ^[0-9]+$ && "$POLL_SECONDS" -gt 0 ]] \
    || die 'IDENTITY_E2E_POLL_SECONDS must be a positive integer.'
  [[ "$EMAIL" =~ ^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.-]+$ ]] || die 'IDENTITY_E2E_EMAIL is invalid.'
  [[ ${#PASSWORD} -ge 12 ]] || die 'Set IDENTITY_E2E_PASSWORD to a unique password of at least 12 characters.'
  [[ "$SHIPPER_PRINCIPAL_ID" =~ ^[1-9][0-9]*$ ]] \
    || die 'Set IDENTITY_E2E_SHIPPER_PRINCIPAL_ID to an existing disposable active shipper principal.'
  docker info >/dev/null 2>&1 || die 'Docker daemon is unavailable.'
  for service in api-gateway auth-service user-service shipper-service postgres kafka; do ensure_running "$service"; done
  compose config --format json | jq -e '
    .services["auth-service"].environment.IDENTITY_EVENTS_ENABLED == "true"
    and .services["auth-service"].environment.IDENTITY_OUTBOX_RELAY_ENABLED == "true"
    and .services["auth-service"].environment.IDENTITY_STATUS_BOOTSTRAP_ENABLED == "true"
    and .services["auth-service"].environment.PUBLIC_REGISTRATION_ENABLED == "true"
    and .services["auth-service"].environment.REGISTRATION_CANARY_PERCENTAGE == "100"
    and .services["user-service"].environment.IDENTITY_EVENTS_ENABLED == "true"
    and .services["user-service"].environment.IDENTITY_OUTBOX_RELAY_ENABLED == "true"
  ' >/dev/null || die 'Compose identity flow is not fully enabled; do not run the final live proof against this flag state.'

  local admin_token registration_request registration principal provisioning_token profile auth account_id profile_id event_id payload before_receipts status_before shipper_status_before
  admin_token="$(read_token)"
  new_curl_config "$admin_token"
  [[ "$(shipper_row "$SHIPPER_PRINCIPAL_ID")" == *":${SHIPPER_PRINCIPAL_ID}:ACTIVE:"* ]] \
    || die 'IDENTITY_E2E_SHIPPER_PRINCIPAL_ID must point to an active linked shipper profile.'

  registration_request="$(jq -n --arg email "$EMAIL" --arg password "$PASSWORD" --arg role USER \
    '{email:$email,password:$password,role:$role}')"
  registration="$(curl --fail-with-body --silent --show-error --max-time 15 \
    -X POST "$BASE/api/auth/register" -H 'Content-Type: application/json' \
    -d "$registration_request")" \
    || die 'Auth registration request failed.'
  principal="$(jq -r '.data.authId // empty' <<< "$registration")"
  provisioning_token="$(jq -r '.data.provisioningToken // empty' <<< "$registration")"
  [[ "$principal" =~ ^[1-9][0-9]*$ && "$provisioning_token" == *.*.* ]] \
    || die 'Auth registration did not return the principal/provisioning token contract.'

  profile="$(curl --fail-with-body --silent --show-error --max-time 15 \
    -X POST "$BASE/api/users/registrations" -H 'Content-Type: application/json' \
    -d "$(jq -n --arg token "$provisioning_token" --arg name 'Identity E2E Fixture' \
      '{provisioningToken:$token,fullName:$name}')")" \
    || die 'User registration handoff failed.'
  profile_id="$(jq -r '.data.id // empty' <<< "$profile")"
  [[ "$profile_id" =~ ^[1-9][0-9]*$ ]] || die 'User registration did not return a profile ID.'

  wait_until 'Auth profile link and lifecycle transition' \
    "row=\$(auth_row); [[ \$row == ${principal}:${profile_id}:PENDING_EMAIL_VERIFICATION:* || \$row == ${principal}:${profile_id}:ACTIVE:* ]]"
  wait_until 'User profile projection' \
    "row=\$(user_row ${principal}); [[ \$row == ${profile_id}:${principal}:* ]]"
  wait_until 'published Auth/User fixture outboxes' "assert_fixture_outboxes_published ${principal}"

  event_id="$(sql user_db "SELECT event_id FROM identity_outbox_events WHERE aggregate_id = ${principal} AND event_type = 'identity.profile.created' ORDER BY id DESC LIMIT 1;")"
  payload="$(sql user_db "SELECT payload FROM identity_outbox_events WHERE event_id = '${event_id}';")"
  [[ "$event_id" =~ ^[0-9a-fA-F-]{36}$ && -n "$payload" ]] || die 'Missing User profile-created outbox event.'
  before_receipts="$(sql auth_db "SELECT count(*) FROM identity_inbox_receipts WHERE event_id = '${event_id}';")"
  [[ "$before_receipts" == 1 ]] || die 'Auth did not record exactly one profile inbox receipt.'
  replay_profile_event "$payload"
  sleep "$POLL_SECONDS"
  [[ "$(sql auth_db "SELECT count(*) FROM identity_inbox_receipts WHERE event_id = '${event_id}';")" == 1 ]] \
    || die 'Profile event replay was not idempotent.'
  [[ "$(auth_row)" == ${principal}:${profile_id}:* ]] || die 'Profile replay changed the Auth profile link.'

  status_before="$(sql user_db "SELECT count(*) FROM identity_inbox_receipts WHERE principal_id = ${principal} AND event_type = 'identity.status.changed';")"
  curl --config "$CURL_CONFIG" --fail-with-body --silent --show-error --max-time 15 \
    -X POST "$BASE/api/auth/admin/accounts/${principal}/block" \
    -H 'Content-Type: application/json' -d '{"reason":"identity-e2e"}' >/dev/null \
    || die 'Admin block request failed.'
  wait_until 'Auth BLOCKED lifecycle' \
    "row=\$(auth_row); [[ \$row == ${principal}:${profile_id}:BLOCKED:*:false ]]"
  wait_until 'User BLOCKED lifecycle projection' \
    "row=\$(user_row ${principal}); [[ \$row == ${profile_id}:${principal}:BLOCKED:*:true:false ]]"
  wait_until 'published block status event' "assert_fixture_outboxes_published ${principal}"
  wait_until 'User block inbox receipt' \
    "[[ \$(sql user_db \"SELECT count(*) FROM identity_inbox_receipts WHERE principal_id = ${principal} AND event_type = 'identity.status.changed';\") -gt ${status_before} ]]"

  curl --config "$CURL_CONFIG" --fail-with-body --silent --show-error --max-time 15 \
    -X POST "$BASE/api/auth/admin/accounts/${principal}/unblock" >/dev/null \
    || die 'Admin unblock request failed.'
  wait_until 'Auth post-unblock lifecycle' \
    "row=\$(auth_row); [[ \$row == ${principal}:${profile_id}:PENDING_EMAIL_VERIFICATION:*:true || \$row == ${principal}:${profile_id}:ACTIVE:*:true ]]"
  wait_until 'User post-unblock projection' \
    "row=\$(user_row ${principal}); [[ \$row == ${profile_id}:${principal}:PENDING_EMAIL_VERIFICATION:*:false:true || \$row == ${profile_id}:${principal}:ACTIVE:*:false:true ]]"
  wait_until 'published unblock status event' "assert_fixture_outboxes_published ${principal}"

  shipper_status_before="$(sql shipper_db "SELECT count(*) FROM identity_inbox_receipts WHERE principal_id = ${SHIPPER_PRINCIPAL_ID} AND event_type = 'identity.status.changed';")"
  curl --config "$CURL_CONFIG" --fail-with-body --silent --show-error --max-time 15 \
    -X POST "$BASE/api/auth/admin/accounts/${SHIPPER_PRINCIPAL_ID}/block" \
    -H 'Content-Type: application/json' -d '{"reason":"identity-e2e-shipper"}' >/dev/null \
    || die 'Admin shipper block request failed.'
  wait_until 'Shipper Auth BLOCKED lifecycle' \
    "row=\$(sql auth_db \"SELECT lifecycle_status || ':' || CASE WHEN is_active THEN 'true' ELSE 'false' END FROM auth_account WHERE id = ${SHIPPER_PRINCIPAL_ID};\"); [[ \$row == BLOCKED:false ]]"
  wait_until 'Shipper BLOCKED lifecycle projection' \
    "row=\$(shipper_row ${SHIPPER_PRINCIPAL_ID}); [[ \$row == *:${SHIPPER_PRINCIPAL_ID}:BLOCKED:* ]]"
  wait_until 'published shipper block status event' "assert_auth_outbox_published ${SHIPPER_PRINCIPAL_ID}"
  wait_until 'Shipper block inbox receipt' \
    "[[ \$(sql shipper_db \"SELECT count(*) FROM identity_inbox_receipts WHERE principal_id = ${SHIPPER_PRINCIPAL_ID} AND event_type = 'identity.status.changed';\") -gt ${shipper_status_before} ]]"

  curl --config "$CURL_CONFIG" --fail-with-body --silent --show-error --max-time 15 \
    -X POST "$BASE/api/auth/admin/accounts/${SHIPPER_PRINCIPAL_ID}/unblock" >/dev/null \
    || die 'Admin shipper unblock request failed.'
  wait_until 'Shipper Auth ACTIVE lifecycle' \
    "row=\$(sql auth_db \"SELECT lifecycle_status || ':' || CASE WHEN is_active THEN 'true' ELSE 'false' END FROM auth_account WHERE id = ${SHIPPER_PRINCIPAL_ID};\"); [[ \$row == ACTIVE:true ]]"
  wait_until 'Shipper ACTIVE lifecycle projection' \
    "row=\$(shipper_row ${SHIPPER_PRINCIPAL_ID}); [[ \$row == *:${SHIPPER_PRINCIPAL_ID}:ACTIVE:* ]]"
  wait_until 'published shipper unblock status event' "assert_auth_outbox_published ${SHIPPER_PRINCIPAL_ID}"
  assert_identity_dlt_empty

  printf 'PASS: identity Compose proof completed for principal %s and profile %s.\n' "$principal" "$profile_id"
}

case "${1:-}" in
  help|-h|--help) usage ;;
  '') main ;;
  *) usage >&2; die "Unknown argument: $1" ;;
esac
