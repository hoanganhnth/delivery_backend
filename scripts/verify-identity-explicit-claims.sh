#!/usr/bin/env bash
set -euo pipefail

# Static release gate for R5. Access-token `sub` may change from legacy profile
# ID to principal ID only when every resource-service HTTP boundary constructs
# its actor from the explicit claims. This check intentionally has no runtime
# dependency and is not a substitute for the outbox/Kafka/error-rate gate.

cd "$(dirname "$0")/.."

die() {
  printf 'Identity explicit-claim verification: %s\n' "$*" >&2
  exit 1
}

require_file() {
  [[ -f "$1" ]] || die "missing required file: $1"
}

require_text() {
  local file="$1" text="$2"
  rg -Fq "$text" "$file" || die "${file} must contain ${text}"
}

converter='auth-resource-server-starter/src/main/java/com/delivery/auth/resourceserver/security/DeliveryJwtAuthenticationConverter.java'
require_file "$converter"
require_text "$converter" 'jwt.getClaim("principal_id")'
require_text "$converter" 'jwt.getClaim("legacy_user_id")'
require_text "$converter" 'jwt.getClaim("identity_claims_version")'
require_text "$converter" 'identityClaimsVersion == null'
require_text "$converter" 'identityClaimsVersion != 1L'
if rg -q 'getSubject\(' "$converter"; then
  die 'the shared access-token converter must not derive identity from JWT sub'
fi

# Maven dependency is the source-of-truth inventory: any service that consumes
# the starter must have its HTTP filter chain wired to the strict converter.
consumer_poms=()
while IFS= read -r pom; do
  [[ -n "$pom" ]] && consumer_poms+=("$pom")
done < <(
  rg -l '<artifactId>auth-resource-server-starter</artifactId>' */pom.xml \
    | grep -v '^auth-resource-server-starter/pom.xml$' \
    | sort
)
(( ${#consumer_poms[@]} > 0 )) || die 'no resource service declares auth-resource-server-starter'

for pom in "${consumer_poms[@]}"; do
  service="${pom%/pom.xml}"
  config="${service}/src/main/java/com/delivery/${service//-/_}/security/SecurityConfig.java"
  require_file "$config"
  require_text "$config" 'DeliveryJwtAuthenticationConverter'
  require_text "$config" 'jwtAuthenticationConverter(converter)'
done

# A resource service reading sub directly bypasses the shared converter and
# would make an Auth-only R5 issuer flip unsafe. Provisioning tokens are a
# different token type: their verifier accepts principal_id first and has a
# documented same-value sub compatibility fallback.
sub_readers=()
while IFS= read -r reader; do
  [[ -n "$reader" ]] && sub_readers+=("$reader")
done < <(
  rg -l 'getSubject\(' --glob '*.java' --glob '!**/test/**' \
    --glob '!auth-service/**' \
    --glob '!user-service/src/main/java/com/delivery/user_service/service/ProvisioningTokenVerifier.java' \
    . || true
)
if (( ${#sub_readers[@]} > 0 )); then
  die "resource code must not read JWT sub directly; found: ${sub_readers[*]}"
fi

# Tracking does its WebSocket handshake outside the HTTP SecurityFilterChain;
# require the same explicit claims and make the no-sub rule visible here.
tracking_ws='tracking-service/src/main/java/com/delivery/tracking_service/config/WebSocketConfig.java'
require_file "$tracking_ws"
require_text "$tracking_ws" 'jwt.getClaim("principal_id")'
require_text "$tracking_ws" 'jwt.getClaim("legacy_user_id")'
require_text "$tracking_ws" 'jwt.getClaim("identity_claims_version")'
if rg -q 'getSubject\(' "$tracking_ws"; then
  die 'Tracking WebSocket must not derive identity from JWT sub'
fi

# Provisioning is a distinct short-lived token, but it has the same Auth-owned
# principal key invariant. Unlike old token compatibility, its consumer must
# not fall back to `sub`: R5 changes the access-token subject and registration
# authority is always the signed principal_id claim.
provisioning_verifier='user-service/src/main/java/com/delivery/user_service/service/ProvisioningTokenVerifier.java'
require_file "$provisioning_verifier"
require_text "$provisioning_verifier" 'jwt.getClaim("principal_id")'
if rg -q 'getSubject\(' "$provisioning_verifier"; then
  die 'provisioning handoff must require principal_id and must not fall back to JWT sub'
fi

user_service='user-service/src/main/java/com/delivery/user_service/service/impl/UserServiceImpl.java'
require_file "$user_service"
require_text "$user_service" '!request.getAuthId().equals(request.getPrincipalId())'
require_text "$user_service" 'authId and principalId must identify the same Auth account'

# Auth account identity is email-based before a principal exists. The lookup
# and its PostgreSQL constraint must use the same canonical rule so case-only
# or whitespace-only retries cannot manufacture a second credential.
auth_repository='auth-service/src/main/java/com/delivery/auth_service/repository/AuthAccountRepository.java'
auth_service='auth-service/src/main/java/com/delivery/auth_service/service/AuthService.java'
auth_email_migration='auth-service/src/main/java/db/migration/V8__canonical_auth_account_email.java'
require_file "$auth_repository"
require_file "$auth_service"
require_file "$auth_email_migration"
require_text "$auth_repository" 'lower(trim(account.email)) = lower(trim(:email))'
require_text "$auth_service" 'private static String normalizeEmail(String email)'
require_text "$auth_email_migration" 'ON auth_account (lower(btrim(email)))'
require_text "$auth_email_migration" 'CREATE UNIQUE INDEX CONCURRENTLY'
require_text "$auth_email_migration" 'DROP INDEX CONCURRENTLY IF EXISTS'

# Gateway must never turn legacy client-supplied identity headers into an
# authority source. It only routes/rate-limits and forwards bearer tokens.
gateway_filter='api-gateway/src/main/java/com/delivery/api_gateway/config/TrustedIdentityHeaderFilter.java'
require_file "$gateway_filter"
require_text "$gateway_filter" 'headers.remove(USER_ID_HEADER)'
require_text "$gateway_filter" 'headers.remove(ROLE_HEADER)'

printf 'PASS: %s resource services use strict explicit JWT claims; no resource boundary reads access-token sub.\n' "${#consumer_poms[@]}"
