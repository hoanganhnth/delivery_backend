#!/usr/bin/env bash
# Keeps the CLI usage aligned with every generated automatic simulator scenario.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output="$(bash "$ROOT_DIR/scripts/sandbox-run.sh" unsupported 2>&1 || true)"

grep -Fq 'network-delay' <<< "$output"
grep -Fq 'shipper-disconnect' <<< "$output"
grep -Fq 'customer-cancel' <<< "$output"
grep -Fq 'scenario-network-delay.json' "$ROOT_DIR/scripts/sandbox-up.sh"
grep -Fq 'scenario-shipper-disconnect.json' "$ROOT_DIR/scripts/sandbox-up.sh"
grep -Fq 'scenario-customer-cancel-after-accept.json' "$ROOT_DIR/scripts/sandbox-up.sh"
grep -Fq 'Recovered simulator URL from the running sandbox port mapping.' "$ROOT_DIR/scripts/sandbox-run.sh"
grep -Fq 'docker compose "${COMPOSE_FILES[@]}" port simulator-service 8100' "$ROOT_DIR/scripts/sandbox-run.sh"
grep -Fq 'SANDBOX_SIMULATOR_ADMIN_TOKEN' "$ROOT_DIR/scripts/sandbox-run.sh"
grep -Fq 'export SIMULATOR_ADMIN_TOKEN' "$ROOT_DIR/scripts/sandbox-run.sh"
if grep -Fq 'export SIMULATOR_ADMIN_TOKEN="$SIMULATOR_ADMIN_TOKEN"' "$ROOT_DIR/scripts/sandbox-run.sh"; then
  exit 1
fi
grep -Fq 'SANDBOX_SIMULATOR_ADMIN_TOKEN=' "$ROOT_DIR/scripts/sandbox-up.sh"
grep -Fq 'env COMPOSE_FILE="$COMPOSE_FILE_VALUE"' "$ROOT_DIR/scripts/sandbox-up.sh"
grep -Fq 'admin_login_body="$(jq -nc' "$ROOT_DIR/scripts/sandbox-up.sh"
grep -Fq 'SEED_AUTH_DIRECT_LOGIN=true' "$ROOT_DIR/scripts/sandbox-up.sh"
grep -Fq 'for attempt in $(seq 1 20)' "$ROOT_DIR/scripts/seed.sh"
