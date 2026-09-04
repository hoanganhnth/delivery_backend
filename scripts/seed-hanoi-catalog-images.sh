#!/usr/bin/env bash
# =============================================================================
# Populate local Hanoi catalog restaurant/menu image URLs.
#
# This runner updates existing rows through the authenticated Restaurant HTTP
# handlers from inside the Docker network. It does not insert or update rows
# with SQL. It is resumable and safe to rerun: matching rows receive the same
# deterministic image URL on every run.
#
# Required explicit flags for a write:
#   LOCAL_BULK_SEED=true
#   ALLOW_LOCAL_EMAIL_VERIFICATION_BYPASS=true
#
# Example:
#   LOCAL_BULK_SEED=true \
#   ALLOW_LOCAL_EMAIL_VERIFICATION_BYPASS=true \
#   DRY_RUN=false \
#   bash scripts/seed-hanoi-catalog-images.sh
#
# The generated manifest contains emails, row IDs and image URLs, never bearer
# tokens.
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
WORKSPACE_DIR="$(cd "$BACKEND_DIR/.." && pwd)"

CATALOG_FILE="${CATALOG_FILE:-$WORKSPACE_DIR/data/catalog/hanoi-catalog.json}"
PASS="${PASS:-Password123!}"
DRY_RUN="${DRY_RUN:-true}"
LOCAL_BULK_SEED="${LOCAL_BULK_SEED:-false}"
ALLOW_LOCAL_EMAIL_VERIFICATION_BYPASS="${ALLOW_LOCAL_EMAIL_VERIFICATION_BYPASS:-false}"
OUTPUT_FILE="${OUTPUT_FILE:-/tmp/delivery-hanoi-catalog-image-seed.json}"
LOGIN_ATTEMPTS="${LOGIN_ATTEMPTS:-20}"
RESTAURANT_LIMIT="${RESTAURANT_LIMIT:-0}"
RESTAURANT_START="${RESTAURANT_START:-0}"

command -v jq >/dev/null || { echo "❌ Cần jq" >&2; exit 2; }
command -v docker >/dev/null || { echo "❌ Cần Docker" >&2; exit 2; }
command -v shasum >/dev/null || { echo "❌ Cần shasum" >&2; exit 2; }
command -v cut >/dev/null || { echo "❌ Cần cut" >&2; exit 2; }
[[ -f "$CATALOG_FILE" ]] || { echo "❌ Không tìm thấy catalog: $CATALOG_FILE" >&2; exit 2; }
[[ "$DRY_RUN" == "true" || "$DRY_RUN" == "false" ]] || {
  echo "❌ DRY_RUN chỉ nhận true hoặc false" >&2
  exit 2
}
if [[ "$DRY_RUN" == "false" ]]; then
  [[ "$LOCAL_BULK_SEED" == "true" ]] || {
    echo "❌ Đây là runner local-only; truyền LOCAL_BULK_SEED=true để xác nhận." >&2
    exit 2
  }
  [[ "$ALLOW_LOCAL_EMAIL_VERIFICATION_BYPASS" == "true" ]] || {
    echo "❌ Cần ALLOW_LOCAL_EMAIL_VERIFICATION_BYPASS=true để xác nhận fixture local." >&2
    exit 2
  }
  [[ "$LOGIN_ATTEMPTS" =~ ^[1-9][0-9]*$ ]] || { echo "❌ LOGIN_ATTEMPTS không hợp lệ" >&2; exit 2; }
  [[ "$RESTAURANT_LIMIT" =~ ^[0-9]+$ ]] || { echo "❌ RESTAURANT_LIMIT không hợp lệ" >&2; exit 2; }
  [[ "$RESTAURANT_START" =~ ^[0-9]+$ ]] || { echo "❌ RESTAURANT_START không hợp lệ" >&2; exit 2; }
fi

if [[ -n "${COMPOSE_FILE:-}" ]]; then
  COMPOSE_COMMAND=(docker compose)
else
  COMPOSE_COMMAND=(docker compose -f "$BACKEND_DIR/docker-compose.yml")
  if [[ -f "$BACKEND_DIR/docker-compose.secrets.yml" ]]; then
    COMPOSE_COMMAND+=( -f "$BACKEND_DIR/docker-compose.secrets.yml" )
  fi
fi

validate_catalog() {
  jq -e '
    .schemaVersion == 1 and
    (.restaurants | type == "array" and length > 0) and
    (.menuItems | type == "array" and length > 0) and
    all(.restaurants[];
      (.restaurantKey | type == "string" and length > 0) and
      (.name | type == "string" and length > 0) and
      (.address | type == "string" and length > 0) and
      (.image | type == "string" and startswith("https://"))
    ) and
    all(.menuItems[];
      (.restaurantKey | type == "string" and length > 0) and
      (.name | type == "string" and length > 0) and
      (.image | type == "string" and startswith("https://"))
    )
  ' "$CATALOG_FILE" >/dev/null || {
    echo "❌ Catalog chưa có image URL HTTPS hợp lệ cho mọi record" >&2
    exit 1
  }
}

owner_email() {
  local key="$1"
  local local_part="catalog-owner+$key"
  if [[ "${#local_part}" -gt 64 ]]; then
    local digest
    digest="$(printf '%s' "$key" | shasum -a 256 | cut -c1-16)"
    local_part="catalog-owner+$digest"
  fi
  printf '%s@test.dev\n' "$local_part"
}

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

login_owner() {
  local key="$1"
  local email response token=""
  email="$(owner_email "$key")"
  for attempt in $(seq 1 "$LOGIN_ATTEMPTS"); do
    response="$(direct_post auth-service http://localhost:8081/api/auth/login \
      "$(jq -cn --arg email "$email" --arg password "$PASS" --arg device "catalog-image-$key" \
        '{email:$email,password:$password,deviceId:$device,deviceName:"Hanoi catalog image seed",deviceType:"WEB"}')")"
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

find_restaurant_id() {
  local restaurant="$1"
  local token="$2"
  local name address list
  name="$(jq -r '.name' <<<"$restaurant")"
  address="$(jq -r '.address' <<<"$restaurant")"
  list="$(direct_get restaurant-service http://localhost:8083/api/restaurants/my-restaurants "$token")"
  jq -e '(.status == 1) and (.data | type == "array")' <<<"$list" >/dev/null || {
    echo "❌ Không đọc được restaurants của owner: $(jq -c '.' <<<"$list" 2>/dev/null || echo "$list")" >&2
    return 1
  }
  jq -r --arg name "$name" --arg address "$address" \
    '.data[] | select(.name == $name and .address == $address) | .id' <<<"$list" | head -n 1
}

update_restaurant_image() {
  local restaurant_id="$1"
  local image="$2"
  local token="$3"
  local response payload
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
  local response payload
  payload="$(jq -cn --arg image "$image" '{image:$image}')"
  response="$(direct_put restaurant-service "http://localhost:8083/api/menu-items/$menu_id" "$payload" "$token")"
  jq -e --arg expected "$image" --arg id "$menu_id" \
    '(.status == 1) and (.data.id == ($id | tonumber)) and (.data.image == $expected)' \
    <<<"$response" >/dev/null || {
      echo "❌ Không cập nhật image menu=$menu_id: $(jq -c '.' <<<"$response" 2>/dev/null || echo "$response")" >&2
      return 1
    }
}

validate_catalog
if [[ "$DRY_RUN" == "true" ]]; then
  echo "🔎 DRY_RUN=true — không gọi Docker/API."
  jq -r '
    "restaurants=" + ((.restaurants | length) | tostring),
    "menuItems=" + ((.menuItems | length) | tostring),
    "restaurantImages=" + (([.restaurants[].image] | unique | length) | tostring),
    "menuImages=" + (([.menuItems[].image] | unique | length) | tostring)
  ' "$CATALOG_FILE"
  exit 0
fi

restaurant_count="$(jq '.restaurants | length' "$CATALOG_FILE")"
target_count="$restaurant_count"
if [[ "$RESTAURANT_START" -ge "$restaurant_count" ]]; then
  target_count=0
else
  target_count=$((restaurant_count - RESTAURANT_START))
  if [[ "$RESTAURANT_LIMIT" -gt 0 && "$RESTAURANT_LIMIT" -lt "$target_count" ]]; then
    target_count="$RESTAURANT_LIMIT"
  fi
fi
echo "🖼️ Hanoi catalog images: $target_count restaurants from index $RESTAURANT_START/$restaurant_count"
echo "   Docker-network API update; existing rows only; no delete/reset."

manifest='[]'
processed=0
while IFS= read -r restaurant; do
  processed=$((processed + 1))
  key="$(jq -r '.restaurantKey' <<<"$restaurant")"
  name="$(jq -r '.name' <<<"$restaurant")"
  restaurant_image="$(jq -r '.image' <<<"$restaurant")"
  echo "[$processed/$target_count] $key — $name" >&2

  owner_token="$(login_owner "$key")"
  restaurant_id="$(find_restaurant_id "$restaurant" "$owner_token")"
  [[ "$restaurant_id" =~ ^[1-9][0-9]*$ ]] || {
    echo "❌ Không tìm thấy restaurant owner=$key name=$name" >&2
    exit 1
  }
  update_restaurant_image "$restaurant_id" "$restaurant_image" "$owner_token"
  echo "  ✓ restaurant id=$restaurant_id" >&2

  menu_response="$(direct_get restaurant-service "http://localhost:8083/api/menu-items/restaurant/$restaurant_id" "$owner_token")"
  jq -e '(.status == 1) and (.data | type == "array")' <<<"$menu_response" >/dev/null || {
    echo "❌ Không đọc được menu restaurant=$restaurant_id: $(jq -c '.' <<<"$menu_response" 2>/dev/null || echo "$menu_response")" >&2
    exit 1
  }
  menu_count=0
  while IFS= read -r item; do
    item_name="$(jq -r '.name' <<<"$item")"
    item_image="$(jq -r '.image' <<<"$item")"
    menu_id="$(jq -r --arg name "$item_name" \
      '.data[] | select(.name == $name) | .id' <<<"$menu_response" | head -n 1)"
    [[ "$menu_id" =~ ^[1-9][0-9]*$ ]] || {
      echo "❌ Không tìm thấy menu $key/$item_name" >&2
      exit 1
    }
    update_menu_image "$menu_id" "$item_image" "$owner_token"
    menu_count=$((menu_count + 1))
  done < <(jq -c --arg key "$key" '.menuItems[] | select(.restaurantKey == $key)' "$CATALOG_FILE")
  echo "  ✓ menu items=$menu_count" >&2

  manifest="$(jq -c --arg key "$key" --arg name "$name" \
    --arg email "$(owner_email "$key")" --argjson restaurantId "$restaurant_id" \
    --argjson menuCount "$menu_count" --arg image "$restaurant_image" \
    '. + [{restaurantKey:$key,name:$name,ownerEmail:$email,restaurantId:$restaurantId,menuCount:$menuCount,image:$image}]' \
    <<<"$manifest")"
done < <(
  if [[ "$RESTAURANT_LIMIT" -gt 0 ]]; then
    end=$((RESTAURANT_START + RESTAURANT_LIMIT))
    jq -c --argjson start "$RESTAURANT_START" --argjson end "$end" '.restaurants[$start:$end][]' "$CATALOG_FILE"
  else
    jq -c --argjson start "$RESTAURANT_START" '.restaurants[$start:][]' "$CATALOG_FILE"
  fi
)

umask 077
printf '%s\n' "$manifest" > "$OUTPUT_FILE"
echo "✅ Image seed xong: $processed restaurants, menu theo catalog"
echo "📄 Manifest: $OUTPUT_FILE"
