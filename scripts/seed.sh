#!/usr/bin/env bash
# =============================================================================
# Seed dữ liệu test cơ bản cho luồng đặt hàng end-to-end.
# Tạo: 1 khách, 1 chủ nhà hàng (+ nhà hàng + 1 menu item), 1 shipper (online +
# có vị trí trong Redis GEO) — đủ điều kiện để đặt đơn và match shipper.
#
# Yêu cầu: cụm docker-compose đang chạy & healthy, có `curl` và `jq`.
# Chạy:   bash scripts/seed.sh
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

BASE="${BASE:-http://localhost:8079}"   # API Gateway (server.port=8079)
PASS="${PASS:-Password123!}"
SHIPPER_DEPOSIT="${SHIPPER_DEPOSIT:-500000}"
RUN_ID="${RUN_ID:-$(date +%s)}"
RUN_SUFFIX="${RUN_ID: -8}"
SEED_OUTPUT_FILE="${SEED_OUTPUT_FILE:-}"
SEED_SKIP_OFFLINE_PREVIOUS_SHIPPERS="${SEED_SKIP_OFFLINE_PREVIOUS_SHIPPERS:-false}"

# ⚠️ Role: các service downstream kiểm tra theo các chuỗi này
#   (USER = khách, SHOP_OWNER = chủ nhà hàng, SHIPPER = shipper).
#   Lưu ý có điểm lệch role đã biết giữa auth và các service — xem
#   docs/product/features/order-lifecycle.md §11.
ROLE_CUSTOMER="USER"
ROLE_OWNER="SHOP_OWNER"
ROLE_SHIPPER="SHIPPER"

# Toạ độ mẫu (TP.HCM) — nhà hàng và shipper gần nhau để match tìm thấy.
REST_LAT="10.7769"; REST_LNG="106.7009"
SHIPPER_LAT="10.7780"; SHIPPER_LNG="106.7020"

command -v jq >/dev/null || { echo "❌ Cần cài jq"; exit 1; }
command -v docker >/dev/null || { echo "❌ Cần Docker để seed ledger ký quỹ local"; exit 1; }

COMPOSE_COMMAND=(docker compose -f "$BACKEND_DIR/docker-compose.yml")
if [[ -f "$BACKEND_DIR/docker-compose.secrets.yml" ]]; then
  COMPOSE_COMMAND+=( -f "$BACKEND_DIR/docker-compose.secrets.yml" )
fi

# Trích token: thử cả dạng bọc BaseResponse (.data) lẫn phẳng.
extract() { jq -r "$1 // .data$1 // empty"; }

register() { # email role
  curl --fail-with-body --silent --show-error -X POST "$BASE/api/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$PASS\",\"role\":\"$2\"}" >/dev/null
}

login() { # email deviceId -> echoes accessToken
  curl --fail-with-body --silent --show-error -X POST "$BASE/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$PASS\",\"deviceId\":\"$2\",\"deviceName\":\"MVP seed\",\"deviceType\":\"WEB\"}" \
    | jq -r '.accessToken // .data.accessToken // empty'
}

offline_previous_seed_shippers() {
  local rows email token cleanup_index=0
  rows="$("${COMPOSE_COMMAND[@]}" exec -T postgres psql \
    -U postgres -d auth_db -At \
    -c "SELECT email FROM auth_account WHERE role = 'SHIPPER' AND email LIKE 'shipper+%@test.dev';" \
    2>/dev/null || true)"
  [[ -z "$rows" ]] && return 0

  while IFS= read -r email; do
    [[ -z "$email" ]] && continue
    cleanup_index=$((cleanup_index + 1))
    token="$(login "$email" "seed-$RUN_ID-cleanup-$cleanup_index" 2>/dev/null || true)"
    [[ -n "$token" ]] || continue
    curl --fail-with-body --silent --show-error -X PATCH \
      "$BASE/api/shippers/online-status?isOnline=false" \
      -H "Authorization: Bearer $token" >/dev/null || true
    curl --fail-with-body --silent --show-error -X POST \
      "$BASE/api/tracking/shipper-locations/offline" \
      -H "Authorization: Bearer $token" >/dev/null || true
  done <<< "$rows"
  echo "✅ Shipper fixture cũ đã được đưa offline"
}

operator_provision_shipper() {
  local email="$1"
  local safe_run_id="${RUN_ID//[^a-zA-Z0-9_.-]/-}"
  "${COMPOSE_COMMAND[@]}" run --rm --build --no-deps \
    --name "auth-shipper-provision-$safe_run_id" \
    -e APP_OPERATOR_SHIPPER_PROVISIONING_ENABLED=true \
    -e APP_OPERATOR_SHIPPER_PROVISIONING_EMAIL="$email" \
    -e APP_OPERATOR_SHIPPER_PROVISIONING_PASSWORD="$PASS" \
    -e APP_OPERATOR_SHIPPER_PROVISIONING_EXIT_AFTER_RUN=true \
    auth-service >/dev/null
}

echo "🌱 Seeding vào $BASE ..."

# --- 1. Khách hàng ---
CUST_EMAIL="customer+$RUN_ID@test.dev"
register "$CUST_EMAIL" "$ROLE_CUSTOMER"
CUST_TOKEN="$(login "$CUST_EMAIL" "seed-$RUN_ID-customer")"
[[ -n "$CUST_TOKEN" ]] || { echo "❌ Login customer không trả access token"; exit 1; }
echo "✅ Khách: $CUST_EMAIL (token ${CUST_TOKEN:+ok})"

# Khách không liên quan dùng để khóa participant authorization của raw WebSocket.
OUTSIDER_EMAIL="outsider+$RUN_ID@test.dev"
register "$OUTSIDER_EMAIL" "$ROLE_CUSTOMER"
OUTSIDER_TOKEN="$(login "$OUTSIDER_EMAIL" "seed-$RUN_ID-outsider")"
[[ -n "$OUTSIDER_TOKEN" ]] || { echo "❌ Login outsider không trả access token"; exit 1; }
echo "✅ Khách ngoài delivery: $OUTSIDER_EMAIL (token ${OUTSIDER_TOKEN:+ok})"

# --- 2. Chủ nhà hàng + nhà hàng + menu ---
OWNER_EMAIL="owner+$RUN_ID@test.dev"
register "$OWNER_EMAIL" "$ROLE_OWNER"
OWNER_TOKEN="$(login "$OWNER_EMAIL" "seed-$RUN_ID-owner")"
[[ -n "$OWNER_TOKEN" ]] || { echo "❌ Login owner không trả access token"; exit 1; }
echo "✅ Chủ NH: $OWNER_EMAIL (token ${OWNER_TOKEN:+ok})"

REST_ID="$(curl --fail-with-body --silent --show-error -X POST "$BASE/api/restaurants" \
  -H "Authorization: Bearer $OWNER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Quán Test\",\"address\":\"123 Lê Lợi, Q1\",\"phone\":\"0900000001\",\"openingHour\":\"08:00\",\"closingHour\":\"22:00\",\"addressLat\":$REST_LAT,\"addressLng\":$REST_LNG,\"description\":\"Seed restaurant\"}" \
  | jq -r '.id // .data.id // empty')"
[[ "$REST_ID" =~ ^[0-9]+$ ]] || { echo "❌ Không tạo được restaurant canonical"; exit 1; }
echo "✅ Nhà hàng id=$REST_ID"

MENU_ID="$(curl --fail-with-body --silent --show-error -X POST "$BASE/api/menu-items" \
  -H "Authorization: Bearer $OWNER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Cơm gà\",\"description\":\"Món seed\",\"price\":45000,\"restaurantId\":$REST_ID}" \
  | jq -r '.id // .data.id // empty')"
[[ "$MENU_ID" =~ ^[0-9]+$ ]] || { echo "❌ Không tạo được menu item canonical"; exit 1; }
echo "✅ Menu item id=$MENU_ID"

# --- 3. Shipper: hồ sơ + online + vị trí ---
if [[ "$SEED_SKIP_OFFLINE_PREVIOUS_SHIPPERS" != "true" ]]; then
  offline_previous_seed_shippers
fi
SHIPPER_EMAIL="shipper+$RUN_ID@test.dev"
operator_provision_shipper "$SHIPPER_EMAIL"
SHIPPER_TOKEN="$(login "$SHIPPER_EMAIL" "seed-$RUN_ID-shipper")"
[[ -n "$SHIPPER_TOKEN" ]] || { echo "❌ Login shipper không trả access token"; exit 1; }
echo "✅ Shipper: $SHIPPER_EMAIL (token ${SHIPPER_TOKEN:+ok})"

curl --fail-with-body --silent --show-error -X POST "$BASE/api/shippers" \
  -H "Authorization: Bearer $SHIPPER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"fullName\":\"Shipper Test\",\"vehicleType\":\"MOTORBIKE\",\"licenseNumber\":\"LIC-$RUN_ID\",\"idCard\":\"ID-$RUN_ID\",\"phone\":\"09$RUN_SUFFIX\",\"licensePlate\":\"59-X1-$RUN_SUFFIX\"}" >/dev/null
echo "✅ Hồ sơ shipper đã tạo"

SHIPPER_USER_ID="$(curl --fail-with-body --silent --show-error "$BASE/api/shippers/my-profile" \
  -H "Authorization: Bearer $SHIPPER_TOKEN" \
  | jq -r '.data.userId // .userId // empty')"
if [[ ! "$SHIPPER_USER_ID" =~ ^[0-9]+$ ]]; then
  echo "❌ Không lấy được userId canonical của shipper; dừng trước khi seed ký quỹ"
  exit 1
fi

# Local-only fixture: append một ledger entry idempotent rồi cập nhật projection
# balance trong cùng transaction. Không mở fake payment/manual deposit qua Gateway.
"${COMPOSE_COMMAND[@]}" exec -T postgres psql \
  -U postgres -d settlement_db \
  -v shipper_id="$SHIPPER_USER_ID" \
  -v deposit_amount="$SHIPPER_DEPOSIT" \
  -f - < "$BACKEND_DIR/scripts/seed-settlement.sql" >/dev/null
echo "✅ Ký quỹ local shipper userId=$SHIPPER_USER_ID: $SHIPPER_DEPOSIT VND"

# Bật online (shipperId lấy từ X-User-Id do gateway set từ JWT)
curl --fail-with-body --silent --show-error -X PATCH "$BASE/api/shippers/online-status?isOnline=true" \
  -H "Authorization: Bearer $SHIPPER_TOKEN" >/dev/null
echo "✅ Shipper online"

# Đẩy vị trí vào Redis GEO (để match GEORADIUS tìm thấy)
curl --fail-with-body --silent --show-error -X POST "$BASE/api/tracking/shipper-locations/update" \
  -H "Authorization: Bearer $SHIPPER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"latitude\":$SHIPPER_LAT,\"longitude\":$SHIPPER_LNG,\"isOnline\":true}" >/dev/null
echo "✅ Vị trí shipper đã cập nhật ($SHIPPER_LAT,$SHIPPER_LNG)"

if [[ -n "$SEED_OUTPUT_FILE" ]]; then
  umask 077
  jq -n \
    --arg runId "$RUN_ID" \
    --arg customerToken "$CUST_TOKEN" \
    --arg outsiderToken "$OUTSIDER_TOKEN" \
    --arg ownerToken "$OWNER_TOKEN" \
    --arg shipperToken "$SHIPPER_TOKEN" \
    --argjson restaurantId "$REST_ID" \
    --argjson menuItemId "$MENU_ID" \
    --argjson shipperUserId "$SHIPPER_USER_ID" \
    '{runId: $runId, customerToken: $customerToken, outsiderToken: $outsiderToken,
      ownerToken: $ownerToken,
      shipperToken: $shipperToken, restaurantId: $restaurantId,
      menuItemId: $menuItemId, shipperUserId: $shipperUserId}' \
    > "$SEED_OUTPUT_FILE"
fi

echo
echo "🎉 Seed xong. Gợi ý đặt đơn thử (đơn thường, không flash sale):"
cat <<EOF
curl -X POST "$BASE/api/orders" \\
  -H "Authorization: Bearer <customer-access-token>" -H 'Content-Type: application/json' \\
  -d '{
    "restaurantId": $REST_ID,
    "deliveryAddress": "456 Nguyễn Huệ, Q1",
    "deliveryLat": 10.7740, "deliveryLng": 106.7040,
    "pickupLat": $REST_LAT, "pickupLng": $REST_LNG,
    "customerName": "Khách Test", "customerPhone": "0900000001",
    "paymentMethod": "COD",
    "items": [ { "menuItemId": $MENU_ID, "quantity": 2, "price": 45000 } ]
  }'
EOF
