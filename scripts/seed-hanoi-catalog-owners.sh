#!/usr/bin/env bash
# =============================================================================
# Seed the Hanoi catalog as resumable local SHOP_OWNER fixtures.
#
# This runner is intentionally local-only. It calls the same Auth/User/
# Restaurant HTTP handlers from inside the Docker network so a 485-restaurant
# fixture does not require weakening the Gateway's public-auth/mutation limits.
# It never writes restaurant/menu rows with SQL.
#
# Required explicit flags:
#   LOCAL_BULK_SEED=true
#   ALLOW_LOCAL_EMAIL_VERIFICATION_BYPASS=true
#
# Example:
#   LOCAL_BULK_SEED=true \
#   ALLOW_LOCAL_EMAIL_VERIFICATION_BYPASS=true \
#   bash scripts/seed-hanoi-catalog-owners.sh
#
# The generated manifest contains test emails and row IDs, never bearer tokens.
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
OUTPUT_FILE="${OUTPUT_FILE:-/tmp/delivery-hanoi-catalog-seed.json}"
PROFILE_LINK_ATTEMPTS="${PROFILE_LINK_ATTEMPTS:-40}"
LOGIN_ATTEMPTS="${LOGIN_ATTEMPTS:-20}"
RESTAURANT_LIMIT="${RESTAURANT_LIMIT:-0}"
RESTAURANT_START="${RESTAURANT_START:-0}"

command -v jq >/dev/null || { echo "❌ Cần jq" >&2; exit 2; }
command -v docker >/dev/null || { echo "❌ Cần Docker" >&2; exit 2; }
command -v grep >/dev/null || { echo "❌ Cần grep" >&2; exit 2; }
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
    echo "❌ Cần ALLOW_LOCAL_EMAIL_VERIFICATION_BYPASS=true để login fixture local." >&2
    exit 2
  }
  [[ "$PROFILE_LINK_ATTEMPTS" =~ ^[1-9][0-9]*$ ]] || { echo "❌ PROFILE_LINK_ATTEMPTS không hợp lệ" >&2; exit 2; }
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
    ([.restaurants[].restaurantKey] | unique | length) == (.restaurants | length) and
    (([.menuItems[].restaurantKey] - [.restaurants[].restaurantKey]) | length) == 0 and
    all(.restaurants[];
      (.restaurantKey | type == "string" and length > 0) and
      (.name | type == "string" and length > 0) and
      (.address | type == "string" and length > 0) and
      (.openingHour | type == "string") and
      (.closingHour | type == "string") and
      (.addressLat | type == "number") and
      (.addressLng | type == "number")
    ) and
    all(.menuItems[];
      (.restaurantKey | type == "string" and length > 0) and
      (.name | type == "string" and length > 0) and
      (.description | type == "string" and length > 0) and
      (.price | type == "number" and . > 0) and
      (.status | . == "AVAILABLE" or . == "SOLD_OUT" or . == "DISCONTINUED")
    )
  ' "$CATALOG_FILE" >/dev/null || {
    echo "❌ Catalog không hợp lệ" >&2
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

auth_row() {
  local email="$1"
  "${COMPOSE_COMMAND[@]}" exec -T postgres psql -U postgres -d auth_db -qAt -c \
    "SELECT id || '|' || COALESCE(user_id::text, '') || '|' || role || '|' || lifecycle_status
       FROM auth_account WHERE email = '$email';" </dev/null
}

activate_local_owner() {
  local email="$1"
  "${COMPOSE_COMMAND[@]}" exec -T postgres psql -U postgres -d auth_db -qAt -c \
    "UPDATE auth_account
        SET email_verification_required = false,
            email_verified_at = COALESCE(email_verified_at, CURRENT_TIMESTAMP),
            lifecycle_status = CASE
              WHEN user_id IS NOT NULL AND COALESCE(is_active, false) THEN 'ACTIVE'
      ELSE lifecycle_status
            END,
            lifecycle_version = CASE
              WHEN user_id IS NOT NULL AND COALESCE(is_active, false)
                   AND lifecycle_status <> 'ACTIVE'
              THEN lifecycle_version + 1
              ELSE lifecycle_version
            END
      WHERE email = '$email' AND role = 'SHOP_OWNER'
      RETURNING id;" </dev/null | grep -Eq '^[0-9]+$' || {
        echo "❌ Không kích hoạt được local owner $email" >&2
        return 1
      }
}

# Return only the response body. HTTP errors are retained as JSON when the
# image's wget supports --content-on-error; callers validate status/data.
direct_post() {
  local service="$1"
  local url="$2"
  local payload="$3"
  local token="${4:-}"
  local -a wget_args=(
    --timeout=120
    --tries=1
    --header='Content-Type: application/json'
    --post-data="$payload"
    "$url"
  )
  if [[ -n "$token" ]]; then
    wget_args=(--header="Authorization: Bearer $token" "${wget_args[@]}")
  fi
  "${COMPOSE_COMMAND[@]}" exec -T "$service" wget -qO- --content-on-error \
    "${wget_args[@]}" </dev/null 2>/dev/null || true
}

direct_get() {
  local service="$1"
  local url="$2"
  local token="${3:-}"
  if [[ -n "$token" ]]; then
    "${COMPOSE_COMMAND[@]}" exec -T "$service" wget -qO- --content-on-error \
      --timeout=120 --tries=1 --header="Authorization: Bearer $token" \
      "$url" </dev/null 2>/dev/null || true
  else
    "${COMPOSE_COMMAND[@]}" exec -T "$service" wget -qO- --content-on-error \
      --timeout=120 --tries=1 "$url" </dev/null 2>/dev/null || true
  fi
}

register_owner_if_needed() {
  local restaurant="$1"
  local key email row account_id user_id role lifecycle response provisioning_token profile_payload
  key="$(jq -r '.restaurantKey' <<<"$restaurant")"
  email="$(owner_email "$key")"
  row="$(auth_row "$email")"

  if [[ -n "$row" ]]; then
    IFS='|' read -r account_id user_id role lifecycle <<<"$row"
    [[ "$role" == "SHOP_OWNER" ]] || {
      echo "❌ Email $email đã tồn tại với role $role, dừng để tránh rebinding" >&2
      return 1
    }
  else
    account_id=""; user_id=""; role=""; lifecycle=""
  fi

  if [[ -z "$user_id" ]]; then
    response="$(direct_post auth-service http://localhost:8081/api/auth/register \
      "$(jq -cn --arg email "$email" --arg password "$PASS" \
        '{email:$email,password:$password,role:"SHOP_OWNER"}')")"
    provisioning_token="$(jq -r '.data.provisioningToken // empty' <<<"$response" 2>/dev/null || true)"
    [[ -n "$provisioning_token" ]] || {
      echo "❌ Không nhận được provisioningToken cho $email: $(jq -c '.' <<<"$response" 2>/dev/null || echo "$response")" >&2
      return 1
    }

    profile_payload="$(jq -cn --arg token "$provisioning_token" --argjson row "$restaurant" \
      '{provisioningToken:$token,
        fullName:("Chủ quán - " + $row.name),
        phone:($row.phone // null),
        address:$row.address}')"
    response="$(direct_post user-service http://localhost:8082/api/users/registrations "$profile_payload")"
    jq -e '(.status == 1) and (.data.id != null)' <<<"$response" >/dev/null || {
      echo "❌ Không tạo được profile cho $email: $(jq -c '.' <<<"$response" 2>/dev/null || echo "$response")" >&2
      return 1
    }
  fi

  for attempt in $(seq 1 "$PROFILE_LINK_ATTEMPTS"); do
    row="$(auth_row "$email")"
    user_id="$(awk -F'|' '{print $2}' <<<"$row")"
    if [[ "$user_id" =~ ^[1-9][0-9]*$ ]]; then
      activate_local_owner "$email" >/dev/null
      return 0
    fi
    sleep 1
  done

  echo "❌ Auth chưa link được User profile cho $email sau $PROFILE_LINK_ATTEMPTS giây" >&2
  return 1
}

login_owner() {
  local key="$1"
  local email="$(owner_email "$key")"
  local response token=""
  for attempt in $(seq 1 "$LOGIN_ATTEMPTS"); do
    activate_local_owner "$email" >/dev/null
    response="$(direct_post auth-service http://localhost:8081/api/auth/login \
      "$(jq -cn --arg email "$email" --arg password "$PASS" --arg device "catalog-owner-$key" \
        '{email:$email,password:$password,deviceId:$device,deviceName:"Hanoi catalog seed",deviceType:"WEB"}')")"
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

ensure_restaurant() {
  local restaurant="$1"
  local token="$2"
  local list response name address restaurant_id payload
  name="$(jq -r '.name' <<<"$restaurant")"
  address="$(jq -r '.address' <<<"$restaurant")"
  list="$(direct_get restaurant-service http://localhost:8083/api/restaurants/my-restaurants "$token")"
  jq -e '(.status == 1) and (.data | type == "array")' <<<"$list" >/dev/null || {
    echo "❌ Không đọc được restaurants của owner: $(jq -c '.' <<<"$list" 2>/dev/null || echo "$list")" >&2
    return 1
  }
  restaurant_id="$(jq -r --arg name "$name" --arg address "$address" \
    '.data[] | select(.name == $name and .address == $address) | .id' <<<"$list" | head -n 1)"

  if [[ -z "$restaurant_id" ]]; then
    payload="$(jq -cn --argjson row "$restaurant" \
      '{name:$row.name,address:$row.address,phone:($row.phone // null),
        openingHour:$row.openingHour,closingHour:$row.closingHour,
        defaultPrepTimeMinutes:30,image:($row.image // null),
        description:$row.description,addressLat:$row.addressLat,addressLng:$row.addressLng}')"
    response="$(direct_post restaurant-service http://localhost:8083/api/restaurants "$payload" "$token")"
    restaurant_id="$(jq -r '.data.id // .id // empty' <<<"$response" 2>/dev/null || true)"
    [[ "$restaurant_id" =~ ^[1-9][0-9]*$ ]] || {
      echo "❌ Không tạo được restaurant $(jq -r '.restaurantKey' <<<"$restaurant"): $(jq -c '.' <<<"$response" 2>/dev/null || echo "$response")" >&2
      return 1
    }
    echo "  + restaurant id=$restaurant_id" >&2
  else
    echo "  = restaurant id=$restaurant_id đã có" >&2
  fi
  printf '%s\n' "$restaurant_id"
}

ensure_menu_items() {
  local restaurant="$1"
  local restaurant_id="$2"
  local token="$3"
  local key item item_name item_status existing_id response payload menu_response
  key="$(jq -r '.restaurantKey' <<<"$restaurant")"
  menu_response="$(direct_get restaurant-service "http://localhost:8083/api/menu-items/restaurant/$restaurant_id" "$token")"
  jq -e '(.status == 1) and (.data | type == "array")' <<<"$menu_response" >/dev/null || {
    echo "❌ Không đọc được menu restaurant=$restaurant_id: $(jq -c '.' <<<"$menu_response" 2>/dev/null || echo "$menu_response")" >&2
    return 1
  }

  while IFS= read -r item; do
    item_name="$(jq -r '.name' <<<"$item")"
    item_status="$(jq -r '.status' <<<"$item")"
    existing_id="$(jq -r --arg name "$item_name" \
      '.data[] | select(.name == $name) | .id' <<<"$menu_response" | head -n 1)"
    if [[ -z "$existing_id" ]]; then
      payload="$(jq -cn --argjson item "$item" --argjson restaurantId "$restaurant_id" \
        '{restaurantId:$restaurantId,name:$item.name,description:$item.description,
          price:$item.price,image:($item.image // null)}')"
      response="$(direct_post restaurant-service http://localhost:8083/api/menu-items "$payload" "$token")"
      existing_id="$(jq -r '.data.id // .id // empty' <<<"$response" 2>/dev/null || true)"
      [[ "$existing_id" =~ ^[1-9][0-9]*$ ]] || {
        echo "❌ Không tạo được menu $key/$item_name: $(jq -c '.' <<<"$response" 2>/dev/null || echo "$response")" >&2
        return 1
      }
      echo "    + menu id=$existing_id — $item_name" >&2
      menu_response="$(jq --argjson id "$existing_id" --argjson item "$item" \
        '.data += [{id:$id,name:$item.name,status:"AVAILABLE"}]' <<<"$menu_response")"
    else
      echo "    = menu id=$existing_id — $item_name đã có" >&2
    fi

    if [[ "$item_status" != "AVAILABLE" ]]; then
      payload="$(jq -cn --argjson item "$item" --argjson restaurantId "$restaurant_id" \
        --arg status "$item_status" \
        '{restaurantId:$restaurantId,name:$item.name,description:$item.description,
          price:$item.price,status:$status,image:($item.image // null)}')"
      response="$(direct_post restaurant-service "http://localhost:8083/api/menu-items/$existing_id" "$payload" "$token")"
      jq -e '(.status == 1) and (.data.id != null)' <<<"$response" >/dev/null || {
        echo "❌ Không cập nhật status menu $existing_id: $(jq -c '.' <<<"$response" 2>/dev/null || echo "$response")" >&2
        return 1
      }
    fi
  done < <(jq -c --arg key "$key" '.menuItems[] | select(.restaurantKey == $key)' "$CATALOG_FILE")
}

validate_catalog
if [[ "$DRY_RUN" == "true" ]]; then
  echo "🔎 DRY_RUN=true — không gọi Docker/API."
  jq -r '"restaurants=" + ((.restaurants | length) | tostring), "menuItems=" + ((.menuItems | length) | tostring), (.restaurants[0:5][] | "- " + .restaurantKey + ": " + .name)' "$CATALOG_FILE"
  exit 0
fi
restaurant_count="$(jq '.restaurants | length' "$CATALOG_FILE")"
menu_count="$(jq '.menuItems | length' "$CATALOG_FILE")"
target_count="$restaurant_count"
if [[ "$RESTAURANT_START" -ge "$restaurant_count" ]]; then
  target_count=0
else
  target_count=$((restaurant_count - RESTAURANT_START))
  if [[ "$RESTAURANT_LIMIT" -gt 0 && "$RESTAURANT_LIMIT" -lt "$target_count" ]]; then
    target_count="$RESTAURANT_LIMIT"
  fi
fi
echo "🌱 Hanoi catalog: $target_count restaurants from index $RESTAURANT_START/$restaurant_count, $menu_count menu items total"
echo "   Docker-network seed; existing rows are reused; no delete/reset."

manifest='[]'
processed=0
while IFS= read -r restaurant; do
  processed=$((processed + 1))
  key="$(jq -r '.restaurantKey' <<<"$restaurant")"
  name="$(jq -r '.name' <<<"$restaurant")"
  echo "[$processed/$restaurant_count] $key — $name" >&2
  register_owner_if_needed "$restaurant"
  owner_token="$(login_owner "$key")"
  restaurant_id="$(ensure_restaurant "$restaurant" "$owner_token")"
  ensure_menu_items "$restaurant" "$restaurant_id" "$owner_token"
  item_count="$(jq --arg key "$key" '[.menuItems[] | select(.restaurantKey == $key)] | length' "$CATALOG_FILE")"
  manifest="$(jq -c --arg key "$key" \
    --arg name "$name" --arg email "$(owner_email "$key")" \
    --argjson restaurantId "$restaurant_id" --argjson menuCount "$item_count" \
    '. + [{restaurantKey:$key,name:$name,ownerEmail:$email,restaurantId:$restaurantId,menuCount:$menuCount}]' \
    <<<"$manifest")"
done < <(
  start="$RESTAURANT_START"
  if [[ "$RESTAURANT_LIMIT" -gt 0 ]]; then
    end=$((RESTAURANT_START + RESTAURANT_LIMIT))
    jq -c --argjson start "$start" --argjson end "$end" '.restaurants[$start:$end][]' "$CATALOG_FILE"
  else
    jq -c --argjson start "$start" '.restaurants[$start:][]' "$CATALOG_FILE"
  fi
)

umask 077
printf '%s\n' "$manifest" > "$OUTPUT_FILE"
echo "✅ Seed xong: $processed owner + profile + restaurant, menu theo catalog"
echo "📄 Manifest (không chứa token): $OUTPUT_FILE"
