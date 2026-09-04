#!/usr/bin/env bash
# =============================================================================
# Populate image URLs for the 35 legacy local restaurant fixtures.
#
# The Hanoi catalog runner handles the 485 catalog rows. This companion runner
# handles the older rows (including Quán Test HÀ ĐÔNG) by resolving their
# existing SHOP_OWNER accounts and calling the authenticated Restaurant HTTP
# handlers. SQL is used only to read IDs/emails; restaurant and menu rows are
# never written with SQL.
#
# Required explicit flags for a write:
#   LOCAL_BULK_SEED=true
#   ALLOW_LOCAL_EMAIL_VERIFICATION_BYPASS=true
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
WORKSPACE_DIR="$(cd "$BACKEND_DIR/.." && pwd)"

DEFAULT_CATALOG_FILE="$WORKSPACE_DIR/data/catalog/hanoi-catalog.json"
if [[ ! -f "$DEFAULT_CATALOG_FILE" ]]; then
  DEFAULT_CATALOG_FILE="$SCRIPT_DIR/fixtures/hanoi-grab-image-manifest.json"
fi
CATALOG_FILE="${CATALOG_FILE:-$DEFAULT_CATALOG_FILE}"
PASS="${PASS:-Password123!}"
DRY_RUN="${DRY_RUN:-true}"
LOCAL_BULK_SEED="${LOCAL_BULK_SEED:-false}"
ALLOW_LOCAL_EMAIL_VERIFICATION_BYPASS="${ALLOW_LOCAL_EMAIL_VERIFICATION_BYPASS:-false}"
OUTPUT_FILE="${OUTPUT_FILE:-/tmp/delivery-legacy-fixture-image-seed.json}"
LOGIN_ATTEMPTS="${LOGIN_ATTEMPTS:-20}"
RESTAURANT_LIMIT="${RESTAURANT_LIMIT:-0}"
RESTAURANT_START="${RESTAURANT_START:-0}"

command -v jq >/dev/null || { echo "❌ Cần jq" >&2; exit 2; }
[[ -f "$CATALOG_FILE" ]] || { echo "❌ Không tìm thấy catalog: $CATALOG_FILE" >&2; exit 2; }
[[ "$DRY_RUN" == "true" || "$DRY_RUN" == "false" ]] || {
  echo "❌ DRY_RUN chỉ nhận true hoặc false" >&2
  exit 2
}
[[ "$RESTAURANT_LIMIT" =~ ^[0-9]+$ ]] || { echo "❌ RESTAURANT_LIMIT không hợp lệ" >&2; exit 2; }
[[ "$RESTAURANT_START" =~ ^[0-9]+$ ]] || { echo "❌ RESTAURANT_START không hợp lệ" >&2; exit 2; }
if [[ "$DRY_RUN" == "false" ]]; then
  command -v docker >/dev/null || { echo "❌ Cần Docker" >&2; exit 2; }
  [[ "$LOCAL_BULK_SEED" == "true" ]] || {
    echo "❌ Đây là runner local-only; truyền LOCAL_BULK_SEED=true để xác nhận." >&2
    exit 2
  }
  [[ "$ALLOW_LOCAL_EMAIL_VERIFICATION_BYPASS" == "true" ]] || {
    echo "❌ Cần ALLOW_LOCAL_EMAIL_VERIFICATION_BYPASS=true để xác nhận fixture local." >&2
    exit 2
  }
  [[ "$LOGIN_ATTEMPTS" =~ ^[1-9][0-9]*$ ]] || { echo "❌ LOGIN_ATTEMPTS không hợp lệ" >&2; exit 2; }
fi

validate_catalog() {
  jq -e '
    .schemaVersion == 1 and
    (.restaurants | type == "array" and length > 0) and
    (.menuItems | type == "array" and length > 0) and
    all(.restaurants[];
      (.image | type == "string" and test("^https://huawei-food-cms\\.grab\\.com/"))
    ) and
    all(.menuItems[];
      (.image | type == "string" and test("^https://huawei-food-cms\\.grab\\.com/"))
    )
  ' "$CATALOG_FILE" >/dev/null || {
    echo "❌ Catalog chưa có ảnh Grab CDN hợp lệ cho mọi record" >&2
    exit 1
  }
}

validate_catalog
RESTAURANT_POOL="$(jq -c '[.restaurants[].image] | unique' "$CATALOG_FILE")"
MENU_POOL="$(jq -c '[.menuItems[].image] | unique' "$CATALOG_FILE")"
RESTAURANT_POOL_SIZE="$(jq 'length' <<<"$RESTAURANT_POOL")"
MENU_POOL_SIZE="$(jq 'length' <<<"$MENU_POOL")"
[[ "$RESTAURANT_POOL_SIZE" -gt 0 && "$MENU_POOL_SIZE" -gt 0 ]] || {
  echo "❌ Pool ảnh Grab rỗng" >&2
  exit 1
}

pool_image() {
  local pool="$1"
  local index="$2"
  jq -r --argjson index "$index" '.[$index % length]' <<<"$pool"
}

if [[ "$DRY_RUN" == "true" ]]; then
  echo "🔎 DRY_RUN=true — không gọi Docker/API."
  echo "catalogFile=$CATALOG_FILE"
  echo "legacyFixtureLimit=35"
  echo "restaurantPoolSize=$RESTAURANT_POOL_SIZE"
  echo "menuPoolSize=$MENU_POOL_SIZE"
  echo "sampleRestaurantImage=$(pool_image "$RESTAURANT_POOL" 0)"
  echo "sampleMenuImage=$(pool_image "$MENU_POOL" 0)"
  exit 0
fi

if [[ -n "${COMPOSE_FILE:-}" ]]; then
  COMPOSE_COMMAND=(docker compose)
else
  COMPOSE_COMMAND=(docker compose -f "$BACKEND_DIR/docker-compose.yml")
  if [[ -f "$BACKEND_DIR/docker-compose.secrets.yml" ]]; then
    COMPOSE_COMMAND+=( -f "$BACKEND_DIR/docker-compose.secrets.yml" )
  fi
fi

direct_post() {
  local service="$1"
  local url="$2"
  local payload="$3"
  local -a wget_args=(
    --timeout=120
    --tries=1
    --header='Content-Type: application/json'
    --post-data="$payload"
    "$url"
  )
  "${COMPOSE_COMMAND[@]}" exec -T "$service" wget -qO- --content-on-error \
    "${wget_args[@]}" </dev/null 2>/dev/null || true
}

direct_put() {
  local service="$1"
  local url="$2"
  local payload="$3"
  local token="$4"
  local -a wget_args=(
    --timeout=120
    --tries=1
    --method=PUT
    --body-data="$payload"
    --header='Content-Type: application/json'
    --header="Authorization: Bearer $token"
    "$url"
  )
  "${COMPOSE_COMMAND[@]}" exec -T "$service" wget -qO- --content-on-error \
    "${wget_args[@]}" </dev/null 2>/dev/null || true
}

direct_get() {
  local service="$1"
  local url="$2"
  local token="$3"
  "${COMPOSE_COMMAND[@]}" exec -T "$service" wget -qO- --content-on-error \
    --timeout=120 --tries=1 --header="Authorization: Bearer $token" \
    "$url" </dev/null 2>/dev/null || true
}

owner_email_for() {
  local creator_id="$1"
  local owner_principal_id="$2"
  if [[ -n "$owner_principal_id" ]]; then
    "${COMPOSE_COMMAND[@]}" exec -T postgres psql -U postgres -d auth_db -qAt -c \
      "SELECT email FROM auth_account WHERE id = $owner_principal_id AND role = 'SHOP_OWNER';" </dev/null
  else
    "${COMPOSE_COMMAND[@]}" exec -T postgres psql -U postgres -d auth_db -qAt -c \
      "SELECT email FROM auth_account WHERE user_id = $creator_id AND role = 'SHOP_OWNER';" </dev/null
  fi
}

login_owner() {
  local email="$1"
  local fixture_id="$2"
  local response token=""
  for attempt in $(seq 1 "$LOGIN_ATTEMPTS"); do
    response="$(direct_post auth-service http://localhost:8081/api/auth/login \
      "$(jq -cn --arg email "$email" --arg password "$PASS" --arg device "legacy-image-$fixture_id" \
        '{email:$email,password:$password,deviceId:$device,deviceName:"Legacy fixture image seed",deviceType:"WEB"}')")"
    token="$(jq -r '.data.accessToken // .accessToken // empty' <<<"$response" 2>/dev/null || true)"
    if [[ -n "$token" ]]; then
      printf '%s\n' "$token"
      return 0
    fi
    sleep 1
  done
  echo "❌ Login owner thất bại $email: $(jq -c '.' <<<"$response" 2>/dev/null || echo "$response")" >&2
  return 1
}

update_restaurant_image() {
  local restaurant_id="$1"
  local image="$2"
  local token="$3"
  local payload response
  payload="$(jq -cn --arg image "$image" '{image:$image}')"
  response="$(direct_put restaurant-service "http://localhost:8083/api/restaurants/$restaurant_id" "$payload" "$token")"
  jq -e --arg expected "$image" --arg id "$restaurant_id" \
    '(.status == 1) and (.data.id == ($id | tonumber)) and (.data.image == $expected)' \
    <<<"$response" >/dev/null || {
      echo "❌ Không cập nhật image restaurant=$restaurant_id: $(jq -c '.' <<<"$response" 2>/dev/null || echo "$response")" >&2
      return 1
    }
}

update_menu_image() {
  local menu_id="$1"
  local image="$2"
  local token="$3"
  local payload response
  payload="$(jq -cn --arg image "$image" '{image:$image}')"
  response="$(direct_put restaurant-service "http://localhost:8083/api/menu-items/$menu_id" "$payload" "$token")"
  jq -e --arg expected "$image" --arg id "$menu_id" \
    '(.status == 1) and (.data.id == ($id | tonumber)) and (.data.image == $expected)' \
    <<<"$response" >/dev/null || {
      echo "❌ Không cập nhật image menu=$menu_id: $(jq -c '.' <<<"$response" 2>/dev/null || echo "$response")" >&2
      return 1
    }
}

legacy_rows="$("${COMPOSE_COMMAND[@]}" exec -T postgres psql -U postgres -d restaurant_db -qAt -F '|' -c \
  "SELECT id, name, creator_id, COALESCE(owner_principal_id::text, '') FROM restaurant ORDER BY id LIMIT 35;" </dev/null)"
legacy_count="$(awk 'NF { count++ } END { print count + 0 }' <<<"$legacy_rows")"
[[ "$legacy_count" == "35" ]] || {
  echo "❌ Cần đúng 35 legacy fixtures, thực tế: $legacy_count" >&2
  exit 1
}

target_count="$legacy_count"
if [[ "$RESTAURANT_START" -ge "$legacy_count" ]]; then
  target_count=0
else
  target_count=$((legacy_count - RESTAURANT_START))
  if [[ "$RESTAURANT_LIMIT" -gt 0 && "$RESTAURANT_LIMIT" -lt "$target_count" ]]; then
    target_count="$RESTAURANT_LIMIT"
  fi
fi
echo "🖼️ Legacy fixture images: $target_count restaurants from index $RESTAURANT_START/$legacy_count"
echo "   Docker-network API update; existing rows only; no delete/reset."

manifest='[]'
processed=0
restaurant_pool_index=0
menu_pool_index=0
while IFS='|' read -r restaurant_id restaurant_name creator_id owner_principal_id; do
  [[ -n "$restaurant_id" ]] || continue
  processed=$((processed + 1))
  email="$(owner_email_for "$creator_id" "$owner_principal_id")"
  [[ "$email" == *'@'* ]] || {
    echo "❌ Không tìm thấy SHOP_OWNER cho restaurant=$restaurant_id creator=$creator_id principal=$owner_principal_id" >&2
    exit 1
  }
  echo "[$processed/$target_count] restaurant=$restaurant_id — $restaurant_name" >&2
  owner_token="$(login_owner "$email" "$restaurant_id")"
  restaurant_image="$(pool_image "$RESTAURANT_POOL" "$restaurant_pool_index")"
  restaurant_pool_index=$((restaurant_pool_index + 1))
  update_restaurant_image "$restaurant_id" "$restaurant_image" "$owner_token"

  menu_response="$(direct_get restaurant-service "http://localhost:8083/api/menu-items/restaurant/$restaurant_id" "$owner_token")"
  jq -e '(.status == 1) and (.data | type == "array")' <<<"$menu_response" >/dev/null || {
    echo "❌ Không đọc được menu restaurant=$restaurant_id: $(jq -c '.' <<<"$menu_response" 2>/dev/null || echo "$menu_response")" >&2
    exit 1
  }
  menu_count=0
  while IFS= read -r menu_id; do
    [[ -n "$menu_id" ]] || continue
    menu_image="$(pool_image "$MENU_POOL" "$menu_pool_index")"
    menu_pool_index=$((menu_pool_index + 1))
    update_menu_image "$menu_id" "$menu_image" "$owner_token"
    menu_count=$((menu_count + 1))
  done < <(jq -r '.data[].id' <<<"$menu_response")
  echo "  ✓ menu items=$menu_count" >&2
  manifest="$(jq -c --arg email "$email" --arg id "$restaurant_id" --arg name "$restaurant_name" \
    --argjson menuCount "$menu_count" --arg restaurantImage "$restaurant_image" \
    '. + [{restaurantId:($id|tonumber),name:$name,ownerEmail:$email,menuCount:$menuCount,restaurantImage:$restaurantImage,imageSourcePlatform:"GrabFood"}]' \
    <<<"$manifest")"
done < <(awk -F'|' -v start="$RESTAURANT_START" -v limit="$RESTAURANT_LIMIT" \
  'NR > start && (limit == 0 || NR <= start + limit)' <<<"$legacy_rows")

umask 077
printf '%s\n' "$manifest" > "$OUTPUT_FILE"
echo "✅ Legacy image seed xong: $processed restaurants"
echo "📄 Manifest: $OUTPUT_FILE"
