#!/usr/bin/env bash
set -euo pipefail

# Performs the JWKS migration as three separately approved Compose waves.
#
# This runner is intentionally for a local/staging Compose deployment only. It
# requires a live legacy Gateway before Wave 1, retains that Gateway through
# Wave 2, and refuses to shorten the 15-minute access-token + 5-minute clock
# skew buffer. Production releases must use immutable images and the platform
# rollout controller described in docs/runbooks/rollout-and-rollback.md.

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly PHASE="${1:-}"
readonly STARTUP_TIMEOUT_SECONDS="${JWKS_STARTUP_TIMEOUT_SECONDS:-600}"
readonly TOKEN_BUFFER_SECONDS="${JWKS_TOKEN_BUFFER_SECONDS:-1200}"
readonly ACTIVE_KID="${JWT_ACTIVE_KID:-auth-key-1}"
readonly EXPECTED_ISSUER="${JWT_ISSUER:-delivery-auth}"
readonly EXPECTED_AUDIENCE="${JWT_AUDIENCE:-delivery-api}"
readonly STATE_FILE="${JWKS_ROLLOUT_STATE_FILE:-${ROOT_DIR}/.jwks-rollout-state}"
readonly -a COMPOSE_FILES=(
  -f "${ROOT_DIR}/docker-compose.yml"
  -f "${ROOT_DIR}/docker-compose.secrets.yml"
)
readonly -a RESOURCE_SERVICES=(
  user-service
  restaurant-service
  order-service
  delivery-service
  search-service
  shipper-service
  settlement-service
  notification-service
  match-service
  tracking-service
  livestream-service
  promotion-service
  analytics-service
  flashsale-service
)

die() {
  printf 'JWKS rollout: %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage:
  bash scripts/rollout-jwks-compose.sh wave1
  JWKS_SMOKE_ACCESS_TOKEN_FILE=/secure/path/access.jwt \
    bash scripts/rollout-jwks-compose.sh verify-token
  JWKS_SMOKE_ACCESS_TOKEN_FILE=/secure/path/access.jwt \
    bash scripts/rollout-jwks-compose.sh wave2
  JWKS_SMOKE_ACCESS_TOKEN_FILE=/secure/path/access.jwt \
    bash scripts/rollout-jwks-compose.sh wave3
  bash scripts/rollout-jwks-compose.sh status

Required preconditions:
  - An existing legacy Compose Gateway is serving the same project. Wave 1
    refuses a Gateway that already serves /.well-known/jwks.json.
  - Docker Desktop is running; .env and the operator-owned secret files are
    present. Never put a token or a PEM value in an argument or committed file.
  - Set JWT_ACTIVE_KID if the deployment does not use the existing auth-key-1
    configuration. The value is public key metadata, not key material.

Safety gates:
  - Wave 2 requires a post-Wave-1 test access token and waits at least 1,200
    seconds (15-minute access-token TTL plus 5-minute skew).
  - Wave 3 requires all resource services to be ready and to accept that token
    through the still-legacy Gateway.
  - The state file defaults to .jwks-rollout-state (gitignored). It records no
    token or private key material. Delete it only when intentionally beginning
    a new migration after the old deployment has been restored or retired.
EOF
}

compose() {
  COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-backend_delivery}" \
    docker compose "${COMPOSE_FILES[@]}" "$@"
}

state_value() {
  local key="$1"
  [[ -f "$STATE_FILE" ]] || return 0
  awk -F= -v expected="$key" '$1 == expected { print substr($0, length(expected) + 2); exit }' "$STATE_FILE"
}

write_state() {
  local phase="$1"
  local wave1_epoch="$2"
  local token_verified_epoch="$3"
  local resource_services_epoch="$4"
  local revision
  revision="$(git -C "$ROOT_DIR" rev-parse HEAD)"

  umask 077
  {
    printf 'phase=%s\n' "$phase"
    printf 'revision=%s\n' "$revision"
    printf 'active_kid=%s\n' "$ACTIVE_KID"
    printf 'wave1_epoch=%s\n' "$wave1_epoch"
    printf 'token_verified_epoch=%s\n' "$token_verified_epoch"
    printf 'resource_services_epoch=%s\n' "$resource_services_epoch"
  } > "${STATE_FILE}.tmp"
  mv "${STATE_FILE}.tmp" "$STATE_FILE"
}

require_command() {
  command -v "$1" >/dev/null || die "Required command is unavailable: $1"
}

preflight() {
  require_command docker
  require_command jq
  require_command curl
  require_command mvn
  require_command awk
  require_command base64
  [[ "$STARTUP_TIMEOUT_SECONDS" =~ ^[0-9]+$ && "$STARTUP_TIMEOUT_SECONDS" -gt 0 ]] \
    || die 'JWKS_STARTUP_TIMEOUT_SECONDS must be a positive integer.'
  [[ "$TOKEN_BUFFER_SECONDS" =~ ^[0-9]+$ && "$TOKEN_BUFFER_SECONDS" -ge 1200 ]] \
    || die 'JWKS_TOKEN_BUFFER_SECONDS must be at least 1200 (15 minutes + 5 minutes skew).'
  [[ -n "$ACTIVE_KID" && -n "$EXPECTED_ISSUER" && -n "$EXPECTED_AUDIENCE" ]] \
    || die 'JWT_ACTIVE_KID, JWT_ISSUER and JWT_AUDIENCE must be non-blank.'
  docker info >/dev/null 2>&1 || die 'Docker daemon is unavailable.'
  compose config --quiet
}

require_running_service() {
  local service="$1"
  local container_id
  container_id="$(compose ps -q "$service")"
  [[ -n "$container_id" ]] || die "Required Compose service is not running: $service"
  [[ "$(docker inspect --format '{{.State.Running}}' "$container_id")" == 'true' ]] \
    || die "Required Compose service is not running: $service"
}

wait_for_readiness() {
  local service="$1"
  local deadline=$((SECONDS + STARTUP_TIMEOUT_SECONDS))
  local container_id state

  while (( SECONDS < deadline )); do
    container_id="$(compose ps -q --all "$service")"
    if [[ -n "$container_id" ]]; then
      state="$(docker inspect --format '{{.State.Status}}' "$container_id" 2>/dev/null || true)"
      if [[ "$state" == 'running' ]] \
          && compose exec -T "$service" wget -q -T 3 -O /dev/null \
            http://localhost:9090/actuator/health/readiness; then
        return 0
      fi
      if [[ "$state" == 'exited' || "$state" == 'dead' || "$state" == 'unhealthy' ]]; then
        compose logs --no-color --tail=160 "$service" >&2 || true
        die "$service failed before readiness."
      fi
    fi
    sleep 2
  done

  compose logs --no-color --tail=160 "$service" >&2 || true
  die "Timed out waiting for readiness: $service"
}

assert_legacy_gateway() {
  require_running_service api-gateway
  if compose exec -T api-gateway wget -q -T 3 -O /dev/null \
    http://localhost:8079/.well-known/jwks.json; then
    die 'Gateway already serves JWKS; it is not a legacy pre-cutover Gateway.'
  fi
}

verify_live_jwks() {
  local jwks
  jwks="$(compose exec -T auth-service wget -q -T 3 -O - \
    http://localhost:8081/.well-known/jwks.json)" \
    || die 'Auth JWKS endpoint did not respond.'

  printf '%s' "$jwks" | jq -e --arg expectedKid "$ACTIVE_KID" '
    (.keys | type == "array" and length > 0)
    and any(.keys[];
      .kty == "RSA"
      and .alg == "RS256"
      and .use == "sig"
      and .kid == $expectedKid
      and (.n | type == "string" and length > 0)
      and (.e | type == "string" and length > 0)
    )
    and all(.keys[];
      .kty == "RSA"
      and .alg == "RS256"
      and .use == "sig"
      and (has("d") | not)
      and (has("p") | not)
      and (has("q") | not)
      and (has("dp") | not)
      and (has("dq") | not)
      and (has("qi") | not)
    )
  ' >/dev/null || die 'JWKS does not contain the expected safe RS256 active key.'
}

require_wave1_state() {
  local phase revision state_kid wave1_epoch current_revision
  [[ -f "$STATE_FILE" ]] || die "Missing Wave 1 state file: $STATE_FILE"
  phase="$(state_value phase)"
  revision="$(state_value revision)"
  state_kid="$(state_value active_kid)"
  wave1_epoch="$(state_value wave1_epoch)"
  current_revision="$(git -C "$ROOT_DIR" rev-parse HEAD)"

  [[ "$phase" == 'wave1' || "$phase" == 'token-verified' || "$phase" == 'wave2' || "$phase" == 'wave3' ]] \
    || die 'State file does not represent a completed Wave 1.'
  [[ "$revision" == "$current_revision" ]] \
    || die 'Git revision changed after Wave 1; restart the rollout from a known deployment state.'
  [[ "$state_kid" == "$ACTIVE_KID" ]] \
    || die 'JWT_ACTIVE_KID changed after Wave 1; restart the rollout from a known deployment state.'
  [[ "$wave1_epoch" =~ ^[0-9]+$ ]] || die 'Wave 1 timestamp is invalid.'
}

require_token_buffer() {
  local wave1_epoch elapsed
  wave1_epoch="$(state_value wave1_epoch)"
  elapsed=$(( $(date +%s) - wave1_epoch ))
  (( elapsed >= TOKEN_BUFFER_SECONDS )) || die \
    "Access-token buffer is incomplete: ${elapsed}s elapsed; require ${TOKEN_BUFFER_SECONDS}s."
}

read_smoke_token() {
  local token_file="${JWKS_SMOKE_ACCESS_TOKEN_FILE:-}"
  [[ -n "$token_file" && -r "$token_file" ]] \
    || die 'Set JWKS_SMOKE_ACCESS_TOKEN_FILE to a readable post-Wave-1 access-token file.'
  local token
  token="$(tr -d '\r\n' < "$token_file")"
  [[ "$token" == *.*.* && "$token" != *[[:space:]]* ]] \
    || die 'JWKS_SMOKE_ACCESS_TOKEN_FILE does not contain one compact JWT.'
  printf '%s' "$token"
}

base64url_decode() {
  local value="$1"
  case $(( ${#value} % 4 )) in
    0) ;;
    2) value+='==' ;;
    3) value+='=' ;;
    *) return 1 ;;
  esac
  value="$(printf '%s' "$value" | tr '_-' '/+')"
  if printf '' | base64 --decode >/dev/null 2>&1; then
    printf '%s' "$value" | base64 --decode
  else
    printf '%s' "$value" | base64 -D
  fi
}

verify_smoke_token() {
  local token="$1"
  local header_segment payload_segment signature_segment header payload wave1_epoch now
  IFS='.' read -r header_segment payload_segment signature_segment <<< "$token"
  [[ -n "$header_segment" && -n "$payload_segment" && -n "$signature_segment" ]] \
    || die 'Smoke token is not a compact JWS.'
  header="$(base64url_decode "$header_segment")" || die 'Cannot decode smoke-token header.'
  payload="$(base64url_decode "$payload_segment")" || die 'Cannot decode smoke-token payload.'
  wave1_epoch="$(state_value wave1_epoch)"
  now="$(date +%s)"

  printf '%s' "$header" | jq -e --arg expectedKid "$ACTIVE_KID" '
    .alg == "RS256" and .kid == $expectedKid
  ' >/dev/null || die 'Smoke token header is not RS256 with the active kid.'
  printf '%s' "$payload" | jq -e \
    --arg expectedIssuer "$EXPECTED_ISSUER" \
    --arg expectedAudience "$EXPECTED_AUDIENCE" \
    --argjson earliestIat "$((wave1_epoch - 60))" \
    --argjson now "$now" '
      .iss == $expectedIssuer
      and (.aud | type == "array" and index($expectedAudience) != null)
      and .token_type == "access"
      and (.sub | type == "string" and length > 0)
      and (.roles | type == "array" and length > 0)
      and (.iat | type == "number" and . >= $earliestIat)
      and (.exp | type == "number" and . > $now)
    ' >/dev/null || die 'Smoke token is missing the post-Wave-1 access-token contract.'
}

authenticated_user_smoke() {
  local token="$1"
  local curl_config
  curl_config="$(mktemp "${TMPDIR:-/tmp}/delivery-jwks-curl.XXXXXX")"
  chmod 600 "$curl_config"
  printf 'header = "Authorization: Bearer %s"\n' "$token" > "$curl_config"
  if ! curl --config "$curl_config" --fail --silent --show-error --max-time 15 \
    http://127.0.0.1:8079/api/users >/dev/null; then
    rm -f "$curl_config"
    die 'JWKS-authenticated GET /api/users failed through Gateway.'
  fi
  rm -f "$curl_config"
}

wave1() {
  preflight
  [[ ! -f "$STATE_FILE" ]] || die "State file already exists: $STATE_FILE"
  assert_legacy_gateway

  printf '%s\n' '[JWKS] Wave 1: package and replace Auth only; legacy Gateway remains active.'
  (cd "$ROOT_DIR" && mvn -q -pl auth-service -am -DskipTests package)
  compose up -d --no-deps --build --force-recreate auth-service
  wait_for_readiness auth-service
  verify_live_jwks
  write_state wave1 "$(date +%s)" '' ''
  printf '%s\n' "[JWKS] Wave 1 passed. Generate a fresh access token through the legacy Gateway, save it in a protected file, then run verify-token."
}

verify_token_phase() {
  local token wave1_epoch resource_services_epoch
  preflight
  require_wave1_state
  verify_live_jwks
  token="$(read_smoke_token)"
  verify_smoke_token "$token"
  wave1_epoch="$(state_value wave1_epoch)"
  resource_services_epoch="$(state_value resource_services_epoch)"
  write_state token-verified "$wave1_epoch" "$(date +%s)" "$resource_services_epoch"
  printf '%s\n' '[JWKS] Fresh access-token contract passed.'
}

wave2() {
  local token wave1_epoch token_verified_epoch
  preflight
  require_wave1_state
  require_token_buffer
  [[ "$(state_value phase)" == 'token-verified' || "$(state_value phase)" == 'wave2' || "$(state_value phase)" == 'wave3' ]] \
    || die 'Run verify-token with a fresh post-Wave-1 access token before Wave 2.'
  token_verified_epoch="$(state_value token_verified_epoch)"
  [[ "$token_verified_epoch" =~ ^[0-9]+$ ]] || die 'The fresh access-token verification timestamp is missing.'
  assert_legacy_gateway
  token="$(read_smoke_token)"
  verify_smoke_token "$token"

  printf '%s\n' '[JWKS] Wave 2: package and replace JWKS resource services; legacy Gateway remains active.'
  (cd "$ROOT_DIR" && mvn -q -pl "$(IFS=,; echo "${RESOURCE_SERVICES[*]}")" -am -DskipTests package)
  compose build "${RESOURCE_SERVICES[@]}"
  for service in "${RESOURCE_SERVICES[@]}"; do
    printf '%s\n' "[JWKS] Wave 2: replace ${service}."
    compose up -d --no-deps --force-recreate "$service"
    wait_for_readiness "$service"
  done
  authenticated_user_smoke "$token"
  wave1_epoch="$(state_value wave1_epoch)"
  write_state wave2 "$wave1_epoch" "$token_verified_epoch" "$(date +%s)"
  printf '%s\n' '[JWKS] Wave 2 passed. Resource services accepted the JWKS token through the legacy Gateway.'
}

wave3() {
  local token wave1_epoch token_verified_epoch resource_services_epoch
  preflight
  require_wave1_state
  [[ "$(state_value phase)" == 'wave2' || "$(state_value phase)" == 'wave3' ]] \
    || die 'Wave 3 requires a completed Wave 2.'
  token="$(read_smoke_token)"
  verify_smoke_token "$token"

  printf '%s\n' '[JWKS] Wave 3: package and replace Gateway after all resource services are ready.'
  (cd "$ROOT_DIR" && mvn -q -pl api-gateway -am -DskipTests package)
  compose up -d --no-deps --build --force-recreate api-gateway
  wait_for_readiness api-gateway
  curl --fail --silent --show-error --max-time 15 \
    http://127.0.0.1:8079/.well-known/jwks.json | jq -e --arg expectedKid "$ACTIVE_KID" '
      (.keys | type == "array") and any(.keys[]; .kid == $expectedKid and .alg == "RS256")
    ' >/dev/null || die 'Gateway does not publish the expected Auth JWKS endpoint.'
  authenticated_user_smoke "$token"
  wave1_epoch="$(state_value wave1_epoch)"
  token_verified_epoch="$(state_value token_verified_epoch)"
  resource_services_epoch="$(state_value resource_services_epoch)"
  write_state wave3 "$wave1_epoch" "$token_verified_epoch" "$resource_services_epoch"
  printf '%s\n' '[JWKS] Wave 3 passed. Gateway cutover and JWKS-authenticated user smoke are green.'
}

status() {
  preflight
  if [[ -f "$STATE_FILE" ]]; then
    printf '%s\n' "[JWKS] state file: $STATE_FILE"
    sed 's/^/  /' "$STATE_FILE"
  else
    printf '%s\n' '[JWKS] no rollout state recorded.'
  fi
  compose ps
}

case "$PHASE" in
  wave1) wave1 ;;
  verify-token) verify_token_phase ;;
  wave2) wave2 ;;
  wave3) wave3 ;;
  status) status ;;
  help|-h|--help|'') usage ;;
  *) usage >&2; die "Unknown phase: $PHASE" ;;
esac
