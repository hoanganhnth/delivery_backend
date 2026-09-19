#!/usr/bin/env bash
# Guards the disposable Web live-E2E topology after the Web BFF migration.
set -euo pipefail

readonly BACKEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly WEB_DIR="$BACKEND_DIR/../delivery_web"

rg -q 'web-bff-service' "$BACKEND_DIR/scripts/sandbox-up.sh"
rg -q 'WEB_BFF_ENCRYPTION_KEY_FILE' "$BACKEND_DIR/scripts/sandbox-up.sh"
rg -q 'api-gateway web-bff-service' "$BACKEND_DIR/scripts/sandbox-up.sh"
rg -A 2 '^  web-bff-service:$' "$BACKEND_DIR/docker-compose.isolated-e2e.yml" | rg -q 'container_name: !reset null'
rg -A 4 '^  web-bff-service:$' "$BACKEND_DIR/docker-compose.sandbox.yml" | rg -q 'container_name: !reset null'
rg -q 'web-bff-service' "$WEB_DIR/scripts/run-live-e2e.sh"
rg -Fq 'PASS="$PASSWORD"' "$WEB_DIR/scripts/run-live-e2e.sh"
rg -Fq 'SEED_SKIP_OUTSIDER=true' "$WEB_DIR/scripts/run-live-e2e.sh"
rg -Fq -- '--arg customerEmail "$CUST_EMAIL"' "$BACKEND_DIR/scripts/seed.sh"
rg -Fq -- '--arg ownerEmail "$OWNER_EMAIL"' "$BACKEND_DIR/scripts/seed.sh"
rg -Fq 'readonly CUSTOMER_EMAIL="$(jq -er '\''.customerEmail'\'' "$SEED_FILE")"' "$WEB_DIR/scripts/run-live-e2e.sh"
rg -Fq 'readonly OWNER_EMAIL="$(jq -er '\''.ownerEmail'\'' "$SEED_FILE")"' "$WEB_DIR/scripts/run-live-e2e.sh"
rg -Fq "apiResponse(page, '/bff/session/login', 'POST')" "$WEB_DIR/e2e/live-smoke.spec.ts"

printf '%s\n' 'Web BFF live-E2E contract is valid.'
