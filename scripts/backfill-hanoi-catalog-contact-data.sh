#!/usr/bin/env bash
# =============================================================================
# Backfill local Hanoi restaurant contact fixtures through the Restaurant API.
#
# This updates address, phone and addressLat/addressLng together for existing
# local rows. It intentionally does not write SQL, create rows, or touch menu
# data. The default is a read-only dry run.
#
# Required explicit flags for a local write:
#   LOCAL_BULK_SEED=true
#   ALLOW_LOCAL_EMAIL_VERIFICATION_BYPASS=true
#   DRY_RUN=false
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
WORKSPACE_DIR="$(cd "$BACKEND_DIR/.." && pwd)"

DEFAULT_CATALOG_FILE="$WORKSPACE_DIR/data/catalog/hanoi-catalog.json"
if [[ ! -f "$DEFAULT_CATALOG_FILE" ]]; then
  DEFAULT_CATALOG_FILE="$SCRIPT_DIR/fixtures/hanoi-restaurant-contact-manifest.json"
fi
CATALOG_FILE="${CATALOG_FILE:-$DEFAULT_CATALOG_FILE}"
PASS="${PASS:-Password123!}"
DRY_RUN="${DRY_RUN:-true}"
LOCAL_BULK_SEED="${LOCAL_BULK_SEED:-false}"
ALLOW_LOCAL_EMAIL_VERIFICATION_BYPASS="${ALLOW_LOCAL_EMAIL_VERIFICATION_BYPASS:-false}"
OUTPUT_FILE="${OUTPUT_FILE:-/tmp/delivery-hanoi-contact-backfill.json}"
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
[[ "$LOGIN_ATTEMPTS" =~ ^[1-9][0-9]*$ ]] || { echo "❌ LOGIN_ATTEMPTS không hợp lệ" >&2; exit 2; }
[[ "$RESTAURANT_LIMIT" =~ ^[0-9]+$ ]] || { echo "❌ RESTAURANT_LIMIT không hợp lệ" >&2; exit 2; }
[[ "$RESTAURANT_START" =~ ^[0-9]+$ ]] || { echo "❌ RESTAURANT_START không hợp lệ" >&2; exit 2; }

if [[ "$DRY_RUN" == "false" ]]; then
  [[ "$LOCAL_BULK_SEED" == "true" ]] || {
    echo "❌ Đây là runner local-only; truyền LOCAL_BULK_SEED=true để xác nhận." >&2
    exit 2
  }
  [[ "$ALLOW_LOCAL_EMAIL_VERIFICATION_BYPASS" == "true" ]] || {
    echo "❌ Cần ALLOW_LOCAL_EMAIL_VERIFICATION_BYPASS=true để login fixture local." >&2
    exit 2
  }
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
    ([.restaurants[].restaurantKey] | unique | length) == (.restaurants | length) and
    ([.restaurants[].phone] | unique | length) == (.restaurants | length) and
    all(.restaurants[];
      (.restaurantKey | type == "string" and length > 0) and
      (.name | type == "string" and length > 0) and
      (.address | type == "string" and length >= 10) and
      (.phone | type == "string" and test("^0[0-9]{9,10}$")) and
      (.addressLat | type == "number" and . >= 8 and . <= 24) and
      (.addressLng | type == "number" and . >= 102 and . <= 110)
    )
  ' "$CATALOG_FILE" >/dev/null || {
    echo "❌ Contact catalog không hợp lệ: cần key/name/address/phone/addressLat/addressLng duy nhất." >&2
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
        echo "❌ Không tìm thấy/kích hoạt được local owner $email" >&2
        return 1
      }
}

# Return only the response body. Tokens are passed in memory and never written
# to the output manifest or printed by this script.
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

login_owner() {
  local key="$1"
  local email
  local response token=""
  email="$(owner_email "$key")"
  for attempt in $(seq 1 "$LOGIN_ATTEMPTS"); do
    if ! activate_local_owner "$email" >/dev/null; then
      return 1
    fi
    response="$(direct_post auth-service http://localhost:8081/api/auth/login \
      "$(jq -cn --arg email "$email" --arg password "$PASS" --arg device "catalog-contact-$key" \
        '{email:$email,password:$password,deviceId:$device,deviceName:"Hanoi contact backfill",deviceType:"WEB"}')")"
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
  local name list restaurant_id
  name="$(jq -r '.name' <<<"$restaurant")"
  list="$(direct_get restaurant-service http://localhost:8083/api/restaurants/my-restaurants "$token")"
  jq -e '(.status == 1) and (.data | type == "array")' <<<"$list" >/dev/null || {
    echo "❌ Không đọc được restaurants của owner $name: $(jq -c '.' <<<"$list" 2>/dev/null || echo "$list")" >&2
    return 1
  }
  restaurant_id="$(jq -r --arg name "$name" \
    '.data[] | select(.name == $name) | .id' <<<"$list" | head -n 1)"
  [[ "$restaurant_id" =~ ^[1-9][0-9]*$ ]] || {
    echo "❌ Không tìm thấy restaurant hiện hữu theo tên: $name" >&2
    return 1
  }
  printf '%s\n' "$restaurant_id"
}

update_restaurant_contact() {
  local restaurant="$1"
  local restaurant_id="$2"
  local token="$3"
  local name address phone lat lng payload response
  name="$(jq -r '.name' <<<"$restaurant")"
  address="$(jq -r '.address' <<<"$restaurant")"
  phone="$(jq -r '.phone' <<<"$restaurant")"
  lat="$(jq -r '.addressLat' <<<"$restaurant")"
  lng="$(jq -r '.addressLng' <<<"$restaurant")"
  payload="$(jq -cn --argjson row "$restaurant" \
    '{address:$row.address,phone:$row.phone,addressLat:$row.addressLat,addressLng:$row.addressLng}')"
  response="$(direct_put restaurant-service "http://localhost:8083/api/restaurants/$restaurant_id" "$payload" "$token")"
  jq -e --argjson id "$restaurant_id" --arg address "$address" --arg phone "$phone" \
    --argjson lat "$lat" --argjson lng "$lng" \
    '(.status == 1) and (.data.id == $id) and (.data.address == $address) and
     (.data.phone == $phone) and (.data.latitude == $lat) and (.data.longitude == $lng)' \
    <<<"$response" >/dev/null || {
      echo "❌ Không cập nhật được contact restaurant=$restaurant_id ($name): $(jq -c '.' <<<"$response" 2>/dev/null || echo "$response")" >&2
      return 1
    }
}

validate_catalog
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

echo "📍 Contact catalog: $restaurant_count restaurants; target=$target_count from index $RESTAURANT_START"
echo "   address + phone + addressLat/addressLng sẽ được cập nhật cùng request PUT."

if [[ "$DRY_RUN" == "true" ]]; then
  echo "🔎 DRY_RUN=true — không gọi Docker/API, không thay đổi dữ liệu."
  jq -r --argjson start "$RESTAURANT_START" --argjson limit "$RESTAURANT_LIMIT" '
    .restaurants[$start:(if $limit > 0 then ($start + $limit) else length end)][:5][] |
    "- " + .restaurantKey + ": " + .name + " | " + .address + " | " + .phone
  ' "$CATALOG_FILE"
  exit 0
fi

echo "⚠️ DRY_RUN=false — cập nhật $target_count restaurant hiện hữu; không tạo/xóa row, không cập nhật menu."
manifest='[]'
processed=0
while IFS= read -r restaurant; do
  processed=$((processed + 1))
  key="$(jq -r '.restaurantKey' <<<"$restaurant")"
  name="$(jq -r '.name' <<<"$restaurant")"
  echo "[$processed/$target_count] $key — $name" >&2
  owner_token="$(login_owner "$key")"
  restaurant_id="$(find_restaurant_id "$restaurant" "$owner_token")"
  update_restaurant_contact "$restaurant" "$restaurant_id" "$owner_token"
  manifest="$(jq -c --arg key "$key" --arg name "$name" \
    --arg address "$(jq -r '.address' <<<"$restaurant")" \
    --arg phone "$(jq -r '.phone' <<<"$restaurant")" \
    --argjson restaurantId "$restaurant_id" \
    --argjson addressLat "$(jq -r '.addressLat' <<<"$restaurant")" \
    --argjson addressLng "$(jq -r '.addressLng' <<<"$restaurant")" \
    '. + [{restaurantKey:$key,name:$name,restaurantId:$restaurantId,address:$address,phone:$phone,addressLat:$addressLat,addressLng:$addressLng}]' \
    <<<"$manifest")"
  echo "  ✅ id=$restaurant_id address/phone/coordinates cập nhật" >&2
done < <(
  if [[ "$RESTAURANT_LIMIT" -gt 0 ]]; then
    end=$((RESTAURANT_START + RESTAURANT_LIMIT))
    jq -c --argjson start "$RESTAURANT_START" --argjson end "$end" '.restaurants[$start:$end][]' "$CATALOG_FILE"
  else
    jq -c --argjson start "$RESTAURANT_START" '.restaurants[$start:][]' "$CATALOG_FILE"
  fi
)

umask 077
mkdir -p "$(dirname "$OUTPUT_FILE")"
printf '%s\n' "$manifest" > "$OUTPUT_FILE"
unset owner_token response payload token PASS
echo "✅ Backfill xong: $processed/$target_count restaurant"
echo "📄 Manifest kết quả (không chứa token): $OUTPUT_FILE"
