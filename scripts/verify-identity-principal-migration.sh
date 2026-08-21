#!/usr/bin/env bash
set -euo pipefail

# Final operator-owned proof gate for the identity/principal migration.
# This script intentionally runs tests and Compose checks; incremental coding
# only uses compile/static checks by the approved workflow.

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

required=(identity-contracts auth-resource-server-starter auth-service user-service shipper-service order-service restaurant-service delivery-service notification-service tracking-service settlement-service promotion-service flashsale-service analytics-service livestream-service)
for module in "${required[@]}"; do [[ -d "$module" ]] || { echo "Missing module: $module" >&2; exit 1; }; done

echo "[1/5] Compile complete backend"
mvn -DskipTests compile
echo "[2/5] Identity/JWT/Auth/User/Shipper tests"
mvn -pl identity-contracts,auth-resource-server-starter,auth-service,user-service,shipper-service -am test
echo "[3/5] Resource ownership tests"
mvn -pl order-service,restaurant-service,delivery-service,notification-service,tracking-service,settlement-service,promotion-service,flashsale-service,analytics-service,livestream-service -am test
echo "[4/5] Static contract/migration checks"
bash scripts/verify-http-api-inventory.sh
bash scripts/verify-compose-config.sh
bash scripts/verify-mvp-polyrepo-contract.sh
bash scripts/verify-identity-explicit-claims.sh
echo "[5/5] Live identity event scenario"
if [[ "${IDENTITY_LIVE_E2E:-false}" == "true" ]]; then
  bash scripts/verify-identity-principal-compose.sh
else
  echo "Skipped live proof. Set IDENTITY_LIVE_E2E=true with IDENTITY_E2E_PASSWORD and IDENTITY_E2E_ADMIN_ACCESS_TOKEN_FILE only after the running Compose topology and admin fixture are ready."
fi
