#!/usr/bin/env bash
set -euo pipefail

# Rehearses the Auth -> User status projection crash/outage window without
# stopping the canonical application containers. The proof uses run-scoped
# Auth/User databases and temporary Auth/User containers on the existing Compose
# network:
#
# 1. User schema is migrated, then the temporary user-service container is
#    stopped to simulate an outage.
# 2. Auth runs with a committed pending status-sync row.
# 3. While User is unavailable, Auth records retry failure and leaves the row
#    pending.
# 4. User starts again, Auth scheduler retries, User applies the idempotent
#    internal block command, and Auth clears the pending marker.

readonly RUN_ID="${AUTH_USER_OUTAGE_RUN_ID:-$(date +%Y%m%d%H%M%S)-$$}"
readonly SAFE_RUN_ID="$(printf '%s' "$RUN_ID" \
  | tr '[:upper:]' '[:lower:]' \
  | sed 's/[^a-z0-9]/_/g')"
readonly CONTAINER_SUFFIX="$(printf '%s' "$SAFE_RUN_ID" | sed 's/_/-/g')"
readonly AUTH_DATABASE="auth_user_outage_auth_${SAFE_RUN_ID}"
readonly USER_DATABASE="auth_user_outage_user_${SAFE_RUN_ID}"
readonly AUTH_CONTAINER="auth-user-outage-auth-${CONTAINER_SUFFIX}"
readonly USER_CONTAINER="auth-user-outage-user-${CONTAINER_SUFFIX}"
readonly TIMEOUT_SECONDS="${AUTH_USER_OUTAGE_TIMEOUT_SECONDS:-180}"
readonly ACCOUNT_ID=910001
readonly USER_ID=910001
readonly ADMIN_ID=1
readonly EMAIL="auth-user-outage-${SAFE_RUN_ID}@test.dev"
readonly BLOCK_REASON="auth-user outage retry proof"
readonly -a COMPOSE_COMMAND=(
  docker compose
  -f docker-compose.yml
  -f docker-compose.secrets.yml
)

for command in docker grep mvn sed tr; do
  command -v "$command" >/dev/null
done

if [[ "$SAFE_RUN_ID" != "$RUN_ID" && "$RUN_ID" =~ [^a-zA-Z0-9_-] ]]; then
  printf 'AUTH_USER_OUTAGE_RUN_ID contains unsupported characters: %s\n' "$RUN_ID" >&2
  exit 1
fi
if [[ -z "$SAFE_RUN_ID" || "$SAFE_RUN_ID" == "_"* ]]; then
  printf 'AUTH_USER_OUTAGE_RUN_ID must start with an alphanumeric character.\n' >&2
  exit 1
fi

if [[ -f .env ]]; then
  INTERNAL_SECRET="${INTERNAL_SECRET:-$(sed -n 's/^INTERNAL_SECRET=//p' .env | tail -n 1)}"
  POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-$(sed -n 's/^POSTGRES_PASSWORD=//p' .env | tail -n 1)}"
  export INTERNAL_SECRET POSTGRES_PASSWORD
fi

if [[ -z "${INTERNAL_SECRET:-}" ]]; then
  printf '%s\n' 'INTERNAL_SECRET must be non-blank for Auth/User outage proof.' >&2
  exit 1
fi
if [[ -z "${POSTGRES_PASSWORD:-}" ]]; then
  printf '%s\n' 'POSTGRES_PASSWORD must be non-blank for Auth/User outage proof.' >&2
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  printf '%s\n' 'Docker daemon is unavailable; Auth/User outage proof was not executed.' >&2
  exit 1
fi
postgres_container="$("${COMPOSE_COMMAND[@]}" ps -q postgres)"
if [[ -z "$postgres_container" \
    || "$(docker inspect --format '{{.State.Running}}' "$postgres_container")" != 'true' ]]; then
  printf '%s\n' 'Compose postgres must be running before Auth/User outage proof.' >&2
  exit 1
fi

auth_database_created=false
user_database_created=false
auth_container_started=false
user_container_started=false

cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM

  if [[ "$auth_container_started" == 'true' || "$user_container_started" == 'true' ]]; then
    docker rm -f "$AUTH_CONTAINER" "$USER_CONTAINER" >/dev/null 2>&1 || true
  fi
  if [[ "${KEEP_AUTH_USER_OUTAGE_ARTIFACTS:-false}" != 'true' ]]; then
    if [[ "$auth_database_created" == 'true' ]]; then
      "${COMPOSE_COMMAND[@]}" exec -T postgres dropdb -U postgres --force "$AUTH_DATABASE" \
        >/dev/null 2>&1 || true
    fi
    if [[ "$user_database_created" == 'true' ]]; then
      "${COMPOSE_COMMAND[@]}" exec -T postgres dropdb -U postgres --force "$USER_DATABASE" \
        >/dev/null 2>&1 || true
    fi
  fi
  exit "$exit_code"
}
trap cleanup EXIT INT TERM

sql_scalar() {
  local database="$1"
  local query="$2"
  "${COMPOSE_COMMAND[@]}" exec -T postgres psql -U postgres -d "$database" -At -c "$query"
}

wait_for_container_log() {
  local container="$1"
  local pattern="$2"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    if docker logs "$container" 2>&1 | grep -F "$pattern" >/dev/null; then
      return 0
    fi
    if [[ "$(docker inspect --format '{{.State.Status}}' "$container" 2>/dev/null || true)" \
        != 'running' ]]; then
      docker logs --tail 160 "$container" >&2 || true
      return 1
    fi
    sleep 1
  done
  docker logs --tail 160 "$container" >&2 || true
  return 1
}

wait_for_sql_true() {
  local database="$1"
  local query="$2"
  local description="$3"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    if [[ "$(sql_scalar "$database" "$query" 2>/dev/null || true)" == '1' ]]; then
      return 0
    fi
    sleep 1
  done
  printf 'Timed out waiting for %s\n' "$description" >&2
  printf 'Auth row: %s\n' \
    "$(sql_scalar "$AUTH_DATABASE" "SELECT concat(user_status_sync_pending, '|', user_status_sync_attempts, '|', coalesce(user_status_sync_last_error, '')) FROM auth_account WHERE id = $ACCOUNT_ID;" 2>/dev/null || true)" >&2
  printf 'User row: %s\n' \
    "$(sql_scalar "$USER_DATABASE" "SELECT concat(is_active, '|', is_blocked, '|', coalesce(block_reason, '')) FROM users WHERE id = $USER_ID;" 2>/dev/null || true)" >&2
  return 1
}

start_user_container() {
  docker rm -f "$USER_CONTAINER" >/dev/null 2>&1 || true
  "${COMPOSE_COMMAND[@]}" run -d --no-deps --name "$USER_CONTAINER" \
    -e "SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/$USER_DATABASE" \
    -e SPRING_DATASOURCE_USERNAME=postgres \
    -e "SPRING_DATASOURCE_PASSWORD=$POSTGRES_PASSWORD" \
    -e SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
    -e "INTERNAL_SECRET=$INTERNAL_SECRET" \
    -e JAVA_TOOL_OPTIONS="-Xmx384m -Xms256m" \
    user-service >/dev/null
  user_container_started=true
  wait_for_container_log "$USER_CONTAINER" 'Started UserServiceApplication'
}

printf '%s\n' '[AUTH-USER-OUTAGE] Package current Auth/User artifacts'
mvn -q -pl auth-service,user-service -DskipTests package
printf '%s\n' '[AUTH-USER-OUTAGE] Build current Auth/User images'
"${COMPOSE_COMMAND[@]}" build auth-service user-service >/dev/null

"${COMPOSE_COMMAND[@]}" exec -T postgres createdb -U postgres "$AUTH_DATABASE"
auth_database_created=true
"${COMPOSE_COMMAND[@]}" exec -T postgres createdb -U postgres "$USER_DATABASE"
user_database_created=true

printf '%s\n' '[AUTH-USER-OUTAGE] Start User once to migrate isolated schema'
start_user_container

printf '%s\n' '[AUTH-USER-OUTAGE] Stop User to simulate outage before Auth retry'
docker rm -f "$USER_CONTAINER" >/dev/null
user_container_started=false

printf '%s\n' '[AUTH-USER-OUTAGE] Start Auth with committed pending sync support'
"${COMPOSE_COMMAND[@]}" run -d --no-deps --name "$AUTH_CONTAINER" \
  -e "SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/$AUTH_DATABASE" \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e "SPRING_DATASOURCE_PASSWORD=$POSTGRES_PASSWORD" \
  -e SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e "USER_SERVICE_URL=http://$USER_CONTAINER:8082" \
  -e "INTERNAL_SECRET=$INTERNAL_SECRET" \
  -e GOOGLE_OAUTH_CLIENT_IDS= \
  -e JWT_ACCESS_TOKEN_TTL_SECONDS=900 \
  -e APP_USER_STATUS_SYNC_POLL_DELAY_MS=1000 \
  -e JAVA_TOOL_OPTIONS="-Xmx384m -Xms256m" \
  auth-service >/dev/null
auth_container_started=true
wait_for_container_log "$AUTH_CONTAINER" 'Started AuthServiceApplication'

printf '%s\n' '[AUTH-USER-OUTAGE] Insert committed pending status-sync fixture'
"${COMPOSE_COMMAND[@]}" exec -T postgres psql -U postgres -d "$USER_DATABASE" \
  -v user_id="$USER_ID" \
  -v auth_id="$ACCOUNT_ID" \
  -v email="$EMAIL" <<'SQL' >/dev/null
INSERT INTO users (
  id, auth_id, email, role, is_active, is_blocked, created_at, updated_at
) VALUES (
  :user_id, :auth_id, :'email', 'USER', true, false, now(), now()
);
SQL

"${COMPOSE_COMMAND[@]}" exec -T postgres psql -U postgres -d "$AUTH_DATABASE" \
  -v account_id="$ACCOUNT_ID" \
  -v user_id="$USER_ID" \
  -v admin_id="$ADMIN_ID" \
  -v email="$EMAIL" \
  -v reason="$BLOCK_REASON" <<'SQL' >/dev/null
INSERT INTO auth_account (
  id, user_id, email, password_hash, role, is_active, created_at, updated_at,
  user_status_sync_pending, user_status_sync_version,
  user_status_sync_admin_id, user_status_sync_block_reason,
  user_status_sync_attempts, user_status_sync_last_error, user_status_sync_updated_at
) VALUES (
  :account_id, :user_id, :'email', 'not-used-in-outage-proof', 'USER', false, now(), now(),
  true, 1, :admin_id, :'reason', 0, null, now()
);
SQL

wait_for_sql_true "$AUTH_DATABASE" \
  "SELECT (user_status_sync_pending = true AND user_status_sync_attempts > 0)::int FROM auth_account WHERE id = $ACCOUNT_ID;" \
  'Auth to record a retryable failure while User is unavailable'

[[ "$(sql_scalar "$USER_DATABASE" \
  "SELECT (is_active = true AND is_blocked = false)::int FROM users WHERE id = $USER_ID;")" == '1' ]] || {
  printf '%s\n' 'User projection changed while user-service was unavailable.' >&2
  exit 1
}

printf '%s\n' '[AUTH-USER-OUTAGE] Restart User and wait for Auth scheduler recovery'
start_user_container

wait_for_sql_true "$AUTH_DATABASE" \
  "SELECT (user_status_sync_pending = false AND user_status_sync_attempts = 0 AND user_status_sync_last_error IS NULL)::int FROM auth_account WHERE id = $ACCOUNT_ID;" \
  'Auth to clear the pending status-sync marker after User recovery'
wait_for_sql_true "$USER_DATABASE" \
  "SELECT (is_active = false AND is_blocked = true AND blocked_by = $ADMIN_ID AND block_reason = '$BLOCK_REASON')::int FROM users WHERE id = $USER_ID;" \
  'User projection to reflect the blocked Auth source of truth'

printf '%s\n' \
  'Auth/User outage retry proof passed: pending row survived outage, retry failure was recorded, User recovery applied block, Auth cleared pending marker.'
