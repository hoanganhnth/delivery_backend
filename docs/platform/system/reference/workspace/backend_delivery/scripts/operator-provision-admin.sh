#!/usr/bin/env bash
# =============================================================================
# Operator-only ADMIN fixture provisioning for local/runtime verification.
#
# This does not use public self-registration and does not patch SQL directly.
# It runs the auth-service one-shot runner, which creates/resumes the Auth
# account and User projection through service-owned code.
#
# Required:
#   ADMIN_EMAIL or APP_OPERATOR_ADMIN_PROVISIONING_EMAIL
#   ADMIN_PASSWORD or APP_OPERATOR_ADMIN_PROVISIONING_PASSWORD
#
# Example:
#   ADMIN_EMAIL=admin@test.dev ADMIN_PASSWORD='...' \
#     bash scripts/operator-provision-admin.sh
# =============================================================================
set -euo pipefail

command -v docker >/dev/null || { echo "Docker is required for ADMIN provisioning." >&2; exit 1; }

ADMIN_EMAIL="${ADMIN_EMAIL:-${APP_OPERATOR_ADMIN_PROVISIONING_EMAIL:-}}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-${APP_OPERATOR_ADMIN_PROVISIONING_PASSWORD:-}}"
RUN_ID="${RUN_ID:-$(date +%s)}"

if [[ -z "$ADMIN_EMAIL" ]]; then
  echo "ADMIN_EMAIL or APP_OPERATOR_ADMIN_PROVISIONING_EMAIL must be set." >&2
  exit 1
fi
if [[ -z "$ADMIN_PASSWORD" ]]; then
  echo "ADMIN_PASSWORD or APP_OPERATOR_ADMIN_PROVISIONING_PASSWORD must be set." >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon is unavailable; ADMIN provisioning was not executed." >&2
  exit 1
fi

# Respect a caller-provided, run-scoped Compose file/project. This is required
# by disposable sandbox verification; falling back to the historical local
# files keeps direct operator use unchanged.
COMPOSE_COMMAND=(docker compose)
if [[ -z "${COMPOSE_FILE:-}" && -f docker-compose.secrets.yml ]]; then
  COMPOSE_COMMAND=(docker compose -f docker-compose.yml -f docker-compose.secrets.yml)
fi

safe_run_id="${RUN_ID//[^a-zA-Z0-9_.-]/-}"

"${COMPOSE_COMMAND[@]}" run --rm --build --no-deps \
  --name "auth-admin-provision-$safe_run_id" \
  -e APP_OPERATOR_ADMIN_PROVISIONING_ENABLED=true \
  -e APP_OPERATOR_ADMIN_PROVISIONING_EMAIL="$ADMIN_EMAIL" \
  -e APP_OPERATOR_ADMIN_PROVISIONING_PASSWORD="$ADMIN_PASSWORD" \
  -e APP_OPERATOR_ADMIN_PROVISIONING_EXIT_AFTER_RUN=true \
  auth-service >/dev/null

printf 'ADMIN fixture provisioned/resumed for %s\n' "$ADMIN_EMAIL"
