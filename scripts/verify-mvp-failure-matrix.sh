#!/usr/bin/env bash
set -euo pipefail

readonly BASE="${BASE:-http://127.0.0.1:8079}"
# FINDING_SHIPPER has a deliberate five-minute Saga compensation guard in the
# canonical runtime. Keep enough margin for the 30-second scheduler poll and
# Kafka/outbox propagation; clean E2E overrides retry settings separately.
readonly FLOW_TIMEOUT_SECONDS="${FLOW_TIMEOUT_SECONDS:-420}"
readonly POLL_SECONDS=2
readonly PASS="${PASS:-Password123!}"
# The clean runner creates all fixture identities through one Gateway peer IP.
# Respect the public-auth retry contract instead of weakening that rate limit.
readonly -a AUTH_CURL_RETRY_ARGS=(--retry 4 --retry-all-errors --retry-max-time 240)

command -v curl >/dev/null
command -v jq >/dev/null
command -v docker >/dev/null

# The runner may inherit a disposable COMPOSE_FILE/COMPOSE_PROJECT_NAME pair.
# In that case every operator fixture and direct database assertion must use
# that exact project rather than falling back to the developer's canonical
# Compose stack.  Keep base + secrets for standalone local invocation.
if [[ -n "${COMPOSE_FILE:-}" ]]; then
  COMPOSE_COMMAND=(docker compose)
else
  COMPOSE_COMMAND=(docker compose -f docker-compose.yml)
  if [[ -f docker-compose.secrets.yml ]]; then
    COMPOSE_COMMAND+=( -f docker-compose.secrets.yml )
  fi
fi

step() {
  printf '[FAILURE] %s\n' "$1"
}

seed_result="$(mktemp)"
response_body="$(mktemp)"
trap 'rm -f "$seed_result" "$response_body"' EXIT
env SEED_OUTPUT_FILE="$seed_result" BASE="$BASE" bash scripts/seed.sh >/dev/null

customer_token="$(jq -er '.customerToken' "$seed_result")"
outsider_token="$(jq -er '.outsiderToken' "$seed_result")"
owner_token="$(jq -er '.ownerToken' "$seed_result")"
primary_shipper_token="$(jq -er '.shipperToken' "$seed_result")"
primary_shipper_id="$(jq -er '.shipperUserId' "$seed_result")"
restaurant_id="$(jq -er '.restaurantId' "$seed_result")"
menu_item_id="$(jq -er '.menuItemId' "$seed_result")"
run_id="$(jq -er '.runId' "$seed_result")"
run_suffix="${run_id: -8}"

expect_status() {
  local expected="$1" method="$2" path="$3" token="${4:-}" body="${5:-}"
  local -a args=(--silent --show-error -o "$response_body" -w '%{http_code}'
    -X "$method" "$BASE$path")
  if [[ -n "$token" ]]; then
    args+=(-H "Authorization: Bearer $token")
  fi
  if [[ -n "$body" ]]; then
    args+=(-H 'Content-Type: application/json' -d "$body")
  fi
  local actual
  actual="$(curl "${args[@]}")"
  if [[ "$actual" != "$expected" ]]; then
    printf 'Expected HTTP %s for %s %s, got %s: %s\n' \
      "$expected" "$method" "$path" "$actual" "$(tr '\n' ' ' < "$response_body")" >&2
    return 1
  fi
}

order_body() {
  local payment_method="$1" extras="${2:-}"
  printf '{"restaurantId":%s,"deliveryAddress":"Failure Matrix Address",' \
    "$restaurant_id"
  printf '"deliveryLat":10.7740,"deliveryLng":106.7040,'
  printf '"customerName":"Failure Customer","customerPhone":"0900000001",'
  printf '"paymentMethod":"%s","items":[{"menuItemId":%s,"quantity":1}]%s}' \
    "$payment_method" "$menu_item_id" "$extras"
}

create_order() {
  local response id
  response="$(curl --fail-with-body --silent --show-error -X POST "$BASE/api/orders" \
    -H "Authorization: Bearer $customer_token" -H 'Content-Type: application/json' \
    -d "$(order_body COD)")"
  id="$(jq -er '.data.id // .id' <<<"$response")"
  [[ "$id" =~ ^[0-9]+$ ]] || return 1
  printf '%s' "$id"
}

confirm_order() {
  local order_id="$1"
  curl --fail-with-body --silent --show-error -X POST \
    "$BASE/api/restaurants/orders/$order_id/confirm" \
    -H "Authorization: Bearer $owner_token" -H 'Content-Type: application/json' \
    -d "{\"restaurantId\":$restaurant_id,\"estimatedPrepTime\":15}" >/dev/null
}

wait_for_status() {
  local path="$1" token="$2" expected="$3" selector="$4"
  local deadline=$((SECONDS + FLOW_TIMEOUT_SECONDS)) response status
  while (( SECONDS < deadline )); do
    response="$(curl --silent --show-error "$BASE$path" \
      -H "Authorization: Bearer $token" || true)"
    status="$(jq -r "$selector // empty" <<<"$response" 2>/dev/null || true)"
    [[ "$status" == "$expected" ]] && return 0
    sleep "$POLL_SECONDS"
  done
  printf 'Timed out waiting for %s to reach %s; last status=%s response=%s\n' \
    "$path" "$expected" "${status:-<empty>}" \
    "$(tr '\n' ' ' <<<"${response:-<empty>}")" >&2
  return 1
}

wait_for_offer() {
  local token="$1" expected_order_id="$2"
  local deadline=$((SECONDS + FLOW_TIMEOUT_SECONDS)) notifications notification
  local notification_order_id="" recovery_endpoint=""
  local offer_response offer_order_id="" offer_status="" recovered_delivery_id=""
  while (( SECONDS < deadline )); do
    notifications="$(curl --silent --show-error "$BASE/api/notifications/unread" \
      -H "Authorization: Bearer $token" || true)"
    notification="$(jq -cer --argjson orderId "$expected_order_id" \
      '[.data[]? | select(.type == "MATCH_FOUND" and .relatedEntityId == $orderId)][0]
        // empty' <<<"$notifications" 2>/dev/null || true)"
    if [[ -n "$notification" ]]; then
      notification_order_id="$(jq -r '.data | fromjson? | .orderId // empty' \
        <<<"$notification")"
      recovery_endpoint="$(jq -r '.data | fromjson? | .recoveryEndpoint // empty' \
        <<<"$notification")"
      if [[ "$notification_order_id" == "$expected_order_id" \
          && "$recovery_endpoint" == "/api/deliveries/offers/current" ]]; then
        offer_response="$(curl --silent --show-error \
          "$BASE$recovery_endpoint" \
          -H "Authorization: Bearer $token" || true)"
        offer_order_id="$(jq -r '.data.orderId // empty' <<<"$offer_response")"
        offer_status="$(jq -r '.data.status // empty' <<<"$offer_response")"
        recovered_delivery_id="$(jq -r '.data.deliveryId // empty' <<<"$offer_response")"
      fi
    fi
    if [[ "$offer_order_id" == "$expected_order_id" \
        && "$offer_status" == "WAIT_SHIPPER_CONFIRM" \
        && "$recovered_delivery_id" =~ ^[0-9]+$ ]]; then
      printf '%s' "$recovered_delivery_id"
      return 0
    fi
    sleep "$POLL_SECONDS"
  done
  printf 'Timed out waiting for offer for order %s\n' "$expected_order_id" >&2
  return 1
}

set_shipper_online() {
  local token="$1" online="$2" latitude="$3" longitude="$4"
  curl --fail-with-body --silent --show-error -X PATCH \
    "$BASE/api/shippers/online-status?isOnline=$online" \
    -H "Authorization: Bearer $token" >/dev/null
  if [[ "$online" == "true" ]]; then
    curl --fail-with-body --silent --show-error -X POST \
      "$BASE/api/tracking/shipper-locations/update" \
      -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
      -d "{\"latitude\":$latitude,\"longitude\":$longitude,\"isOnline\":true}" \
      >/dev/null
  else
    curl --fail-with-body --silent --show-error -X POST \
    "$BASE/api/tracking/shipper-locations/offline" \
      -H "Authorization: Bearer $token" >/dev/null
  fi
}

operator_provision_shipper() {
  local email="$1" suffix="$2"
  local safe_run_id="${run_id//[^a-zA-Z0-9_.-]/-}"
  "${COMPOSE_COMMAND[@]}" run --rm --build --no-deps \
    --name "auth-shipper-provision-$safe_run_id-$suffix" \
    -e APP_OPERATOR_SHIPPER_PROVISIONING_ENABLED=true \
    -e APP_OPERATOR_SHIPPER_PROVISIONING_EMAIL="$email" \
    -e APP_OPERATOR_SHIPPER_PROVISIONING_PASSWORD="$PASS" \
    -e APP_OPERATOR_SHIPPER_PROVISIONING_EXIT_AFTER_RUN=true \
    auth-service >/dev/null
}

step "auth, role, MVP payment and voucher failures are fail-closed"
expect_status 401 GET /api/deliveries/offers/current
expect_status 403 GET /api/deliveries/offers/current "$customer_token"
expect_status 400 POST /api/orders "$customer_token" "$(order_body ONLINE)"
expect_status 400 POST /api/orders "$customer_token" "$(order_body COD ',"voucherIds":[999999]')"

step "restaurant rejection converges Order and Delivery without settlement"
rejected_order_id="$(create_order)"
expect_status 403 POST "/api/restaurants/orders/$rejected_order_id/reject" \
  "$outsider_token" "{\"restaurantId\":$restaurant_id,\"reason\":\"forbidden\"}"
curl --fail-with-body --silent --show-error -X POST \
  "$BASE/api/restaurants/orders/$rejected_order_id/reject" \
  -H "Authorization: Bearer $owner_token" -H 'Content-Type: application/json' \
  -d "{\"restaurantId\":$restaurant_id,\"reason\":\"failure matrix\"}" >/dev/null
wait_for_status "/api/orders/$rejected_order_id" "$customer_token" CANCELLED \
  '.data.status // .status'
wait_for_status "/api/deliveries/order/$rejected_order_id" "$customer_token" CANCELLED \
  '.data.status // .status'
expect_status 403 GET "/api/orders/$rejected_order_id" "$outsider_token"
rejected_ledger_count="$("${COMPOSE_COMMAND[@]}" exec -T postgres psql -U postgres \
  -d settlement_db -At -c \
  "SELECT count(*) FROM transactions WHERE order_id = $rejected_order_id;")"
[[ "$rejected_ledger_count" == "0" ]]

step "no online shipper converges both Order and Delivery to SHIPPER_NOT_FOUND"
set_shipper_online "$primary_shipper_token" false 10.7780 106.7020
sleep 3
not_found_order_id="$(create_order)"
confirm_order "$not_found_order_id"
wait_for_status "/api/orders/$not_found_order_id" "$customer_token" SHIPPER_NOT_FOUND \
  '.data.status // .status'
wait_for_status "/api/deliveries/order/$not_found_order_id" "$customer_token" SHIPPER_NOT_FOUND \
  '.data.status // .status'

step "cancel-assignment rematches to a different eligible shipper"
set_shipper_online "$primary_shipper_token" true 10.7780 106.7020
sleep 5

secondary_email="secondary-shipper+$run_id@test.dev"
operator_provision_shipper "$secondary_email" "secondary"
secondary_shipper_token="$(curl "${AUTH_CURL_RETRY_ARGS[@]}" --fail-with-body --silent --show-error -X POST \
  "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$secondary_email\",\"password\":\"$PASS\",\"deviceId\":\"matrix-$run_id-secondary\",\"deviceName\":\"Failure matrix\",\"deviceType\":\"WEB\"}" \
  | jq -er '.accessToken // .data.accessToken')"
curl --fail-with-body --silent --show-error -X POST "$BASE/api/shippers" \
  -H "Authorization: Bearer $secondary_shipper_token" -H 'Content-Type: application/json' \
  -d "{\"fullName\":\"Secondary Shipper\",\"vehicleType\":\"MOTORBIKE\",\"licenseNumber\":\"SECOND-$run_id\",\"idCard\":\"SECOND-ID-$run_id\",\"phone\":\"08$run_suffix\",\"licensePlate\":\"60-B8-$run_suffix\"}" \
  >/dev/null
secondary_shipper_id="$(curl --fail-with-body --silent --show-error \
  "$BASE/api/shippers/my-profile" -H "Authorization: Bearer $secondary_shipper_token" \
  | jq -er '.data.userId // .userId')"
"${COMPOSE_COMMAND[@]}" exec -T postgres psql -U postgres -d settlement_db \
  -v shipper_id="$secondary_shipper_id" -v deposit_amount=500000 \
  -f - < scripts/seed-settlement.sql >/dev/null

rematch_order_id="$(create_order)"
confirm_order "$rematch_order_id"
rematch_delivery_id="$(wait_for_offer "$primary_shipper_token" "$rematch_order_id")"
curl --fail-with-body --silent --show-error -X POST "$BASE/api/deliveries/accept" \
  -H "Authorization: Bearer $primary_shipper_token" -H 'Content-Type: application/json' \
  -d "{\"orderId\":$rematch_order_id,\"action\":\"ACCEPT\",\"currentLat\":10.7780,\"currentLng\":106.7020}" \
  >/dev/null
set_shipper_online "$secondary_shipper_token" true 10.7785 106.7025
sleep 5
curl --fail-with-body --silent --show-error -X POST \
  "$BASE/api/deliveries/cancel-assignment" \
  -H "Authorization: Bearer $primary_shipper_token" -H 'Content-Type: application/json' \
  -d "{\"orderId\":$rematch_order_id,\"reason\":\"failure matrix rematch\"}" \
  >/dev/null
secondary_delivery_id="$(wait_for_offer "$secondary_shipper_token" "$rematch_order_id")"
[[ "$secondary_delivery_id" == "$rematch_delivery_id" ]]
curl --fail-with-body --silent --show-error -X POST "$BASE/api/deliveries/accept" \
  -H "Authorization: Bearer $secondary_shipper_token" -H 'Content-Type: application/json' \
  -d "{\"orderId\":$rematch_order_id,\"action\":\"ACCEPT\",\"currentLat\":10.7785,\"currentLng\":106.7025}" \
  >/dev/null
expect_status 400 PUT "/api/deliveries/$rematch_delivery_id/status?status=DELIVERED" \
  "$secondary_shipper_token"
expect_status 403 PUT "/api/deliveries/$rematch_delivery_id/status?status=PICKED_UP" \
  "$customer_token"
for status in PICKED_UP DELIVERING DELIVERED; do
  curl --fail-with-body --silent --show-error -X PUT \
    "$BASE/api/deliveries/$rematch_delivery_id/status?status=$status" \
    -H "Authorization: Bearer $secondary_shipper_token" >/dev/null
done
expect_status 200 PUT "/api/deliveries/$rematch_delivery_id/status?status=DELIVERED" \
  "$secondary_shipper_token"

ledger_deadline=$((SECONDS + FLOW_TIMEOUT_SECONDS))
rematch_ledger_count=0
while (( SECONDS < ledger_deadline )); do
  rematch_ledger_count="$("${COMPOSE_COMMAND[@]}" exec -T postgres psql -U postgres \
    -d settlement_db -At -c \
    "SELECT count(*) FROM transactions WHERE order_id = $rematch_order_id;")"
  [[ "$rematch_ledger_count" == "4" ]] && break
  sleep "$POLL_SECONDS"
done
[[ "$rematch_ledger_count" == "4" ]]

printf 'MVP failure matrix passed: rejected=%s, not-found=%s, rematched=%s delivery=%s.\n' \
  "$rejected_order_id" "$not_found_order_id" "$rematch_order_id" "$rematch_delivery_id"
