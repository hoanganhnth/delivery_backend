#!/usr/bin/env bash
set -euo pipefail

# Creates an isolated checkout of the last pre-JWKS release and starts it as
# the legacy side of the local/staging JWKS rollout. The checkout stays in
# place as the rollback source until Wave 3 and post-cutover smoke are complete.

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly LEGACY_WORKTREE_DIR="${JWKS_LEGACY_WORKTREE_DIR:-${ROOT_DIR}/../.jwks-legacy-release}"
readonly STARTUP_TIMEOUT_SECONDS="${JWKS_LEGACY_STARTUP_TIMEOUT_SECONDS:-300}"

die() {
  printf 'JWKS legacy bootstrap: %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage:
  bash scripts/bootstrap-jwks-legacy-compose.sh

Optional environment:
  JWKS_LEGACY_REF=<git-ref>          Explicit pre-JWKS release. The default is
                                      discovered from the commit that added the
                                      shared resource-server starter.
  JWKS_LEGACY_WORKTREE_DIR=<path>    Persistent worktree for rollback source.
  COMPOSE_PROJECT_NAME=<name>        Defaults to backend_delivery and must be
                                      reused by rollout-jwks-compose.sh.

Safety:
  - Refuses to run if the canonical Compose project already has containers.
  - Does not copy .env or .secrets; it creates local symlinks in the legacy
    worktree so operator-owned secret files remain in one place.
  - Does not remove the legacy worktree or containers. Keep both until Wave 3
    and the post-cutover smoke are accepted.
EOF
}

compose_current() {
  COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-backend_delivery}" \
    docker compose -f "${ROOT_DIR}/docker-compose.yml" -f "${ROOT_DIR}/docker-compose.secrets.yml" "$@"
}

compose_legacy() {
  COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-backend_delivery}" \
    docker compose -f "${LEGACY_WORKTREE_DIR}/docker-compose.yml" \
      -f "${LEGACY_WORKTREE_DIR}/docker-compose.secrets.yml" "$@"
}

discover_legacy_ref() {
  if [[ -n "${JWKS_LEGACY_REF:-}" ]]; then
    git -C "$ROOT_DIR" rev-parse --verify "${JWKS_LEGACY_REF}^{commit}"
    return
  fi

  local jwks_introduction_commit
  jwks_introduction_commit="$(git -C "$ROOT_DIR" log --diff-filter=A --format=%H \
    -- auth-resource-server-starter/pom.xml | tail -n 1)"
  [[ -n "$jwks_introduction_commit" ]] \
    || die 'Cannot discover the commit that introduced auth-resource-server-starter; set JWKS_LEGACY_REF explicitly.'
  git -C "$ROOT_DIR" rev-parse --verify "${jwks_introduction_commit}^"
}

wait_for_legacy_gateway() {
  local deadline=$((SECONDS + STARTUP_TIMEOUT_SECONDS))
  local container_id state
  while (( SECONDS < deadline )); do
    container_id="$(compose_legacy ps -q api-gateway)"
    if [[ -n "$container_id" ]]; then
      state="$(docker inspect --format '{{.State.Status}}' "$container_id" 2>/dev/null || true)"
      if [[ "$state" == 'running' ]] \
          && compose_legacy exec -T api-gateway wget -q -T 3 -O /dev/null \
            http://localhost:9090/actuator/health/readiness; then
        return 0
      fi
      if [[ "$state" == 'exited' || "$state" == 'dead' || "$state" == 'unhealthy' ]]; then
        compose_legacy logs --no-color --tail=160 api-gateway >&2 || true
        die 'Legacy Gateway failed before readiness.'
      fi
    fi
    sleep 2
  done
  compose_legacy logs --no-color --tail=160 api-gateway >&2 || true
  die 'Timed out waiting for legacy Gateway readiness.'
}

bootstrap() {
  local legacy_ref current_project
  command -v docker >/dev/null || die 'Docker CLI is unavailable.'
  command -v git >/dev/null || die 'Git is unavailable.'
  command -v mvn >/dev/null || die 'Maven is unavailable.'
  [[ "$STARTUP_TIMEOUT_SECONDS" =~ ^[0-9]+$ && "$STARTUP_TIMEOUT_SECONDS" -gt 0 ]] \
    || die 'JWKS_LEGACY_STARTUP_TIMEOUT_SECONDS must be a positive integer.'
  docker info >/dev/null 2>&1 || die 'Docker daemon is unavailable.'
  [[ -f "${ROOT_DIR}/.env" ]] || die "Missing ${ROOT_DIR}/.env."
  [[ -d "${ROOT_DIR}/.secrets" ]] || die "Missing ${ROOT_DIR}/.secrets. Generate or mount operator secrets first."

  current_project="${COMPOSE_PROJECT_NAME:-backend_delivery}"
  [[ -z "$(compose_current ps -aq)" ]] \
    || die "Compose project ${current_project} already has containers; do not bootstrap over an existing stack."
  [[ ! -e "$LEGACY_WORKTREE_DIR" ]] \
    || die "Legacy worktree path already exists: $LEGACY_WORKTREE_DIR"

  legacy_ref="$(discover_legacy_ref)"
  printf '[JWKS] Creating legacy worktree at %s from %s\n' "$LEGACY_WORKTREE_DIR" "$legacy_ref"
  git -C "$ROOT_DIR" worktree add --detach "$LEGACY_WORKTREE_DIR" "$legacy_ref"
  ln -s "${ROOT_DIR}/.env" "${LEGACY_WORKTREE_DIR}/.env"
  ln -s "${ROOT_DIR}/.secrets" "${LEGACY_WORKTREE_DIR}/.secrets"

  printf '%s\n' '[JWKS] Packaging and starting the legacy pre-JWKS Compose stack.'
  (cd "$LEGACY_WORKTREE_DIR" && mvn -q -DskipTests package)
  compose_legacy up -d --build
  wait_for_legacy_gateway

  printf '%s\n' '[JWKS] Legacy Gateway is ready. Continue from the current checkout with:'
  printf '  cd %s\n' "$ROOT_DIR"
  printf '  COMPOSE_PROJECT_NAME=%s bash scripts/rollout-jwks-compose.sh wave1\n' "$current_project"
}

case "${1:-}" in
  help|-h|--help) usage ;;
  '') bootstrap ;;
  *) usage >&2; die "Unknown argument: $1" ;;
esac
