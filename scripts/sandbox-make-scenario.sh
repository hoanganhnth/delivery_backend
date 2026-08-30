#!/usr/bin/env bash
# Build a redacted-on-screen, token-bearing scenario file from sandbox seed
# output. The file is intentionally run-scoped and should remain mode 0600.
set -euo pipefail

readonly SEED_FILE="${1:?Usage: sandbox-make-scenario.sh seed.json output.json [happy|restaurant-reject|no-shipper|shipper-reject|offer-timeout|customer-cancel|customer-cancel-after-accept|shipper-disconnect|network-delay|human-order]}"
readonly OUTPUT_FILE="${2:?Usage: sandbox-make-scenario.sh seed.json output.json [happy|restaurant-reject|no-shipper|shipper-reject|offer-timeout|customer-cancel|customer-cancel-after-accept|shipper-disconnect|network-delay|human-order]}"
readonly KIND="${3:-happy}"

[[ -f "$SEED_FILE" ]] || { echo "Seed file does not exist: $SEED_FILE" >&2; exit 2; }
command -v jq >/dev/null || { echo "jq is required" >&2; exit 2; }
command -v uuidgen >/dev/null || { echo "uuidgen is required" >&2; exit 2; }

case "$KIND" in
  happy|restaurant-reject|no-shipper|shipper-reject|offer-timeout|customer-cancel|customer-cancel-after-accept|shipper-disconnect|network-delay|human-order)
    ;;
  *)
    echo "Unsupported scenario kind: $KIND" >&2
    exit 2
    ;;
esac

jq -e '
  (.customerToken | type == "string" and length > 0) and
  (.ownerToken | type == "string" and length > 0) and
  (.shipperToken | type == "string" and length > 0) and
  (.restaurantId | type == "number" and . > 0) and
  (.menuItemId | type == "number" and . > 0) and
  (.shipperUserId | type == "number" and . > 0)
' "$SEED_FILE" >/dev/null || {
  echo "Seed output is missing the required actor IDs/tokens: $SEED_FILE" >&2
  exit 1
}

mkdir -p "$(dirname "$OUTPUT_FILE")"
umask 077

restaurant_lat="${SANDBOX_RESTAURANT_LAT:-$(jq -r '.restaurantLat // 10.7769' "$SEED_FILE")}"
restaurant_lng="${SANDBOX_RESTAURANT_LNG:-$(jq -r '.restaurantLng // 106.7009' "$SEED_FILE")}"
customer_lat="${SANDBOX_CUSTOMER_LAT:-$(jq -r '.customerLat // 10.7740' "$SEED_FILE")}"
customer_lng="${SANDBOX_CUSTOMER_LNG:-$(jq -r '.customerLng // 106.7040' "$SEED_FILE")}"
menu_price="${SANDBOX_MENU_PRICE:-$(jq -r '.menuPrice // 45000' "$SEED_FILE")}"
shipper_lat="${SANDBOX_SHIPPER_LAT:-$(jq -r '.shipperLat // 10.7780' "$SEED_FILE")}"
shipper_lng="${SANDBOX_SHIPPER_LNG:-$(jq -r '.shipperLng // 106.7020' "$SEED_FILE")}"
order_count="${SANDBOX_ORDER_COUNT:-1}"
shipper_speed_kmh="${SANDBOX_SHIPPER_SPEED_KMH:-30}"
[[ "$order_count" =~ ^[1-9][0-9]*$ ]] || { echo "SANDBOX_ORDER_COUNT must be a positive integer" >&2; exit 2; }
[[ "$shipper_speed_kmh" =~ ^[1-9][0-9]*$ ]] || { echo "SANDBOX_SHIPPER_SPEED_KMH must be a positive integer" >&2; exit 2; }

# Managed actor-pool scenarios must reference Auth principals, never persist
# the fixture access tokens. The seed tokens are only used locally to derive
# their server-owned principal identity before Simulator replaces them with
# short-lived run bindings.
principal_id() {
  local token="$1" payload
  payload="$(printf '%s===' "$(cut -d. -f2 <<<"$token" | tr '_-' '/+')" | base64 -D 2>/dev/null || \
    printf '%s===' "$(cut -d. -f2 <<<"$token" | tr '_-' '/+')" | base64 -d 2>/dev/null)"
  jq -er '.principal_id // .sub | tonumber' <<<"$payload"
}

customer_principal_id="$(principal_id "$(jq -r '.customerToken' "$SEED_FILE")")"
owner_principal_id="$(principal_id "$(jq -r '.ownerToken' "$SEED_FILE")")"
shipper_principal_id="$(principal_id "$(jq -r '.shipperToken' "$SEED_FILE")")"
SHIPPERS_JSON='[]'
if jq -e '.shippers | type == "array" and length > 0' "$SEED_FILE" >/dev/null; then
  while IFS= read -r shipper; do
    token="$(jq -r '.token' <<< "$shipper")"
    principal="$(principal_id "$token")"
    SHIPPERS_JSON="$(jq -c --arg id "$(jq -r '.id' <<< "$shipper")" \
      --arg name "$(jq -r '.id' <<< "$shipper")" --arg token "$token" \
      --argjson userId "$(jq -r '.userId' <<< "$shipper")" --argjson principalId "$principal" \
      --argjson lat "$(jq -r '.lat' <<< "$shipper")" --argjson lng "$(jq -r '.lng' <<< "$shipper")" \
      '. + [{id:$id,userId:$userId,principalId:$principalId,name:$name,token:$token,initialLat:$lat,initialLng:$lng,currentLat:$lat,currentLng:$lng,isOnline:true,codBalance:500000,completedDeliveries:0,behavior:"AUTO_ACCEPT",reactionDelaySeconds:1,speedKmH:30}]' <<< "$SHIPPERS_JSON")"
  done < <(jq -c '.shippers[]' "$SEED_FILE")
else
  SHIPPERS_JSON="$(jq -nc --arg id 'sandbox-shipper-1' --arg token "$(jq -r '.shipperToken' "$SEED_FILE")" --argjson userId "$(jq -r '.shipperUserId' "$SEED_FILE")" \
    --argjson principalId "$shipper_principal_id" --argjson lat "$shipper_lat" --argjson lng "$shipper_lng" \
    '[{id:$id,userId:$userId,principalId:$principalId,name:"Sandbox Shipper 1",token:$token,initialLat:$lat,initialLng:$lng,currentLat:$lat,currentLng:$lng,isOnline:true,codBalance:500000,completedDeliveries:0,behavior:"AUTO_ACCEPT",reactionDelaySeconds:1,speedKmH:30}]')"
fi
cohort_id="${SANDBOX_COHORT_ID:-$(jq -r '.simulationCohortId // empty' "$SEED_FILE")}"
[[ -n "$cohort_id" ]] || cohort_id="$(uuidgen | tr '[:upper:]' '[:lower:]')"
jq -ne --arg cohort "$cohort_id" '($cohort | test("^[0-9a-fA-F-]{36}$"))' >/dev/null || {
  echo "SANDBOX_COHORT_ID must be a UUID" >&2
  exit 2
}

case "$KIND" in
  happy)
    title="Sandbox happy COD"
    order_mode="SIMULATED_ORDER"
    auto_confirm=true
    shipper_online=true
    shipper_behavior="AUTO_ACCEPT"
    triggers='[]'
    expected="DELIVERED"
    ;;
  restaurant-reject)
    title="Sandbox restaurant rejection"
    order_mode="SIMULATED_ORDER"
    auto_confirm=false
    shipper_online=true
    shipper_behavior="AUTO_ACCEPT"
    triggers='[{"enabled":true,"type":"RESTAURANT_REJECT","atStage":"PENDING","delaySecondsAfterStage":0}]'
    expected="REJECTED"
    ;;
  no-shipper)
    title="Sandbox no eligible shipper"
    order_mode="SIMULATED_ORDER"
    auto_confirm=true
    shipper_online=false
    shipper_behavior="TIMEOUT_IGNORE"
    triggers='[]'
    expected="SHIPPER_NOT_FOUND"
    ;;
  shipper-reject)
    title="Sandbox shipper rejection"
    order_mode="SIMULATED_ORDER"
    auto_confirm=true
    shipper_online=true
    shipper_behavior="REJECT_AFTER_DELAY"
    triggers='[]'
    expected="SHIPPER_NOT_FOUND"
    ;;
  offer-timeout)
    title="Sandbox shipper offer timeout"
    order_mode="SIMULATED_ORDER"
    auto_confirm=true
    shipper_online=true
    shipper_behavior="TIMEOUT_IGNORE"
    triggers='[]'
    expected="SHIPPER_NOT_FOUND"
    ;;
  customer-cancel)
    title="Sandbox customer cancellation"
    order_mode="SIMULATED_ORDER"
    auto_confirm=true
    shipper_online=true
    shipper_behavior="AUTO_ACCEPT"
    triggers='[{"enabled":true,"type":"CUSTOMER_CANCEL","atStage":"ASSIGNED","delaySecondsAfterStage":0}]'
    expected="CANCELLED"
    ;;
  customer-cancel-after-accept)
    title="Sandbox customer cancellation after shipper acceptance"
    order_mode="SIMULATED_ORDER"
    auto_confirm=true
    shipper_online=true
    shipper_behavior="AUTO_ACCEPT"
    triggers='[{"enabled":true,"type":"CUSTOMER_CANCEL","atStage":"ASSIGNED","delaySecondsAfterStage":0}]'
    expected="CANCELLED"
    ;;
  shipper-disconnect)
    title="Sandbox shipper disconnect"
    order_mode="SIMULATED_ORDER"
    auto_confirm=true
    shipper_online=true
    shipper_behavior="TIMEOUT_IGNORE"
    triggers='[{"enabled":true,"type":"SHIPPER_DISCONNECT","atStage":"WAIT_SHIPPER_CONFIRM","delaySecondsAfterStage":0}]'
    expected="SHIPPER_NOT_FOUND"
    ;;
  network-delay)
    title="Sandbox transient Gateway poll fault"
    order_mode="SIMULATED_ORDER"
    auto_confirm=true
    shipper_online=true
    shipper_behavior="AUTO_ACCEPT"
    triggers='[{"enabled":true,"type":"NETWORK_DELAY","atStage":"FINDING_SHIPPER","delaySecondsAfterStage":0}]'
    expected="DELIVERED"
    ;;
  human-order)
    title="Sandbox human Delivery App order"
    order_mode="HUMAN_ORDER"
    auto_confirm=true
    shipper_online=true
    shipper_behavior="AUTO_ACCEPT"
    triggers='[]'
    expected="DELIVERED"
    ;;
esac

jq -n \
  --slurpfile seed "$SEED_FILE" \
  --arg title "$title" \
  --arg kind "$KIND" \
  --arg orderMode "$order_mode" \
  --argjson orderCount "$order_count" \
  --argjson shipperSpeedKmh "$shipper_speed_kmh" \
  --argjson restaurantLat "$restaurant_lat" \
  --argjson restaurantLng "$restaurant_lng" \
  --argjson customerLat "$customer_lat" \
  --argjson customerLng "$customer_lng" \
  --argjson shipperLat "$shipper_lat" \
  --argjson shipperLng "$shipper_lng" \
  --argjson menuPrice "$menu_price" \
  --argjson autoConfirm "$auto_confirm" \
  --argjson shipperOnline "$shipper_online" \
  --arg shipperBehavior "$shipper_behavior" \
  --argjson shippers "$SHIPPERS_JSON" \
  --argjson triggers "$triggers" \
  --arg expected "$expected" \
  --arg cohortId "$cohort_id" \
  --argjson customerPrincipalId "$customer_principal_id" \
  --argjson ownerPrincipalId "$owner_principal_id" \
  --argjson shipperPrincipalId "$shipper_principal_id" \
  '($seed[0]) as $s |
   {
     schemaVersion: 1,
     title: $title,
     cohortId: $cohortId,
     description: "Production-like sandbox scenario; all actors and money are synthetic",
     orderMode: $orderMode,
     orderCount: $orderCount,
     customer: {
       principalId: $customerPrincipalId,
       token: $s.customerToken,
       name: "Sandbox Customer",
       phone: "0900000000",
       deliveryAddress: "456 Nguyễn Huệ, Quận 1, TP.HCM",
       lat: $customerLat,
       lng: $customerLng,
       paymentMethod: "COD",
       itemQuantity: 1
     },
     restaurant: {
       ownerPrincipalId: $ownerPrincipalId,
       id: $s.restaurantId,
       menuItemId: $s.menuItemId,
       name: "Sandbox Quán",
       pickupAddress: "123 Lê Lợi, Quận 1, TP.HCM",
       lat: $restaurantLat,
       lng: $restaurantLng,
       menuItemPrice: $menuPrice,
       autoConfirm: $autoConfirm,
       ownerToken: $s.ownerToken,
       prepTimeMinutes: 1
     },
     shippers: ($shippers | map(.isOnline = $shipperOnline | .behavior = $shipperBehavior | .speedKmH = $shipperSpeedKmh)),
     triggers: $triggers,
     assertions: [{id: ("expected-" + $kind), expectedTerminalState: $expected}]
   }' \
  > "$OUTPUT_FILE"

chmod 600 "$OUTPUT_FILE"
echo "Scenario created: $OUTPUT_FILE (kind=$KIND, orders=$order_count; tokens are not printed)"
