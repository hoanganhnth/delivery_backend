#!/usr/bin/env bash
# Build a redacted-on-screen, token-bearing scenario file from sandbox seed
# output. The file is intentionally run-scoped and should remain mode 0600.
set -euo pipefail

readonly SEED_FILE="${1:?Usage: sandbox-make-scenario.sh seed.json output.json [happy|restaurant-reject|no-shipper|human-order]}"
readonly OUTPUT_FILE="${2:?Usage: sandbox-make-scenario.sh seed.json output.json [happy|restaurant-reject|no-shipper|human-order]}"
readonly KIND="${3:-happy}"

[[ -f "$SEED_FILE" ]] || { echo "Seed file does not exist: $SEED_FILE" >&2; exit 2; }
command -v jq >/dev/null || { echo "jq is required" >&2; exit 2; }

case "$KIND" in
  happy|restaurant-reject|no-shipper|human-order)
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
  --argjson triggers "$triggers" \
  --arg expected "$expected" \
  '($seed[0]) as $s |
   {
     schemaVersion: 1,
     title: $title,
     description: "Production-like sandbox scenario; all actors and money are synthetic",
     orderMode: $orderMode,
     customer: {
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
     shippers: [{
       id: "sandbox-shipper-1",
       userId: $s.shipperUserId,
       name: "Sandbox Shipper 1",
       token: $s.shipperToken,
       initialLat: $shipperLat,
       initialLng: $shipperLng,
       currentLat: $shipperLat,
       currentLng: $shipperLng,
       isOnline: $shipperOnline,
       codBalance: 500000,
       behavior: $shipperBehavior,
       reactionDelaySeconds: 1,
       speedKmH: 30
     }],
     triggers: $triggers,
     assertions: [{id: ("expected-" + $kind), expectedTerminalState: $expected}]
   }' \
  > "$OUTPUT_FILE"

chmod 600 "$OUTPUT_FILE"
echo "Scenario created: $OUTPUT_FILE (kind=$KIND; tokens are not printed)"
