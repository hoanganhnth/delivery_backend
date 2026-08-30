#!/usr/bin/env bash
# Verifies that repeated generated scenarios retain the Auth-owned actor cohort.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
temp_dir="$(mktemp -d)"
trap 'rm -rf "$temp_dir"' EXIT

seed="$temp_dir/seed.json"
scenario="$temp_dir/scenario.json"
cohort_id="11111111-1111-4111-8111-111111111111"
token_for() {
  local principal="$1"
  printf 'x.%s.y' "$(printf '{"principal_id":%s}' "$principal" | base64 | tr -d '\n' | tr '+/' '-_')"
}

jq -n \
  --arg cohort "$cohort_id" \
  --arg customer "$(token_for 101)" \
  --arg owner "$(token_for 102)" \
  --arg shipper "$(token_for 103)" \
  '{simulationCohortId:$cohort,customerToken:$customer,ownerToken:$owner,shipperToken:$shipper,
    restaurantId:1,menuItemId:1,shipperUserId:1,restaurantLat:10.7769,restaurantLng:106.7009,
    shipperLat:10.7780,shipperLng:106.7020,customerLat:10.7740,customerLng:106.7040,menuPrice:45000}' \
  > "$seed"

bash "$ROOT_DIR/scripts/sandbox-make-scenario.sh" "$seed" "$scenario" network-delay >/dev/null

test "$(jq -r '.cohortId' "$scenario")" = "$cohort_id"
test "$(jq -r '.triggers[0].type' "$scenario")" = NETWORK_DELAY
test "$(jq -r '.assertions[0].expectedTerminalState' "$scenario")" = DELIVERED
test "$(jq -r '.shippers[0].completedDeliveries' "$scenario")" = 0

SANDBOX_ORDER_COUNT=3 bash "$ROOT_DIR/scripts/sandbox-make-scenario.sh" "$seed" "$scenario" happy >/dev/null
test "$(jq -r '.orderCount' "$scenario")" = 3
test "$(jq -r '.triggers | length' "$scenario")" = 0
SANDBOX_SHIPPER_SPEED_KMH=120 bash "$ROOT_DIR/scripts/sandbox-make-scenario.sh" "$seed" "$scenario" happy >/dev/null
test "$(jq -r '.shippers[0].speedKmH' "$scenario")" = 120

bash "$ROOT_DIR/scripts/sandbox-make-scenario.sh" "$seed" "$scenario" customer-cancel-after-accept >/dev/null
test "$(jq -r '.shippers[0].behavior' "$scenario")" = AUTO_ACCEPT
test "$(jq -r '.triggers[0].type' "$scenario")" = CUSTOMER_CANCEL
test "$(jq -r '.triggers[0].atStage' "$scenario")" = ASSIGNED
test "$(jq -r '.assertions[0].expectedTerminalState' "$scenario")" = CANCELLED
