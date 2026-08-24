#!/usr/bin/env bash
# =============================================================================
# Seed a dated, source-referenced restaurant/menu fixture through Gateway.
#
# Safe default: DRY_RUN=true. To mutate a disposable local environment:
#   OWNER_TOKEN='<SHOP_OWNER access token>' DRY_RUN=false \
#     bash scripts/seed-realistic-catalog.sh
#
# This script intentionally does not write SQL, create accounts, seed orders,
# or enable voucher/flash-sale checkout. Use scripts/seed.sh for the minimal
# end-to-end identity/shipper prerequisite, then pass its owner access token.
# Re-running with DRY_RUN=false creates new rows because the current catalog API
# has no fixture namespace/upsert contract.
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
WORKSPACE_DIR="$(cd "$BACKEND_DIR/.." && pwd)"
DEFAULT_CATALOG_FILE="$WORKSPACE_DIR/data/catalog/realistic-catalog.json"
if [[ ! -f "$DEFAULT_CATALOG_FILE" ]]; then
  DEFAULT_CATALOG_FILE="$SCRIPT_DIR/fixtures/realistic-catalog.json"
fi
CATALOG_FILE="${CATALOG_FILE:-$DEFAULT_CATALOG_FILE}"
BASE="${BASE:-http://localhost:8079}"
OWNER_TOKEN="${OWNER_TOKEN:-}"
DRY_RUN="${DRY_RUN:-true}"

command -v jq >/dev/null || { echo "❌ Cần cài jq" >&2; exit 1; }
command -v curl >/dev/null || { echo "❌ Cần cài curl" >&2; exit 1; }
[[ -f "$CATALOG_FILE" ]] || { echo "❌ Không tìm thấy $CATALOG_FILE" >&2; exit 1; }
[[ "$DRY_RUN" == "true" || "$DRY_RUN" == "false" ]] \
  || { echo "❌ DRY_RUN chỉ nhận true hoặc false" >&2; exit 1; }

if [[ "$DRY_RUN" == "false" && -z "$OWNER_TOKEN" ]]; then
  echo "❌ DRY_RUN=false cần OWNER_TOKEN của tài khoản SHOP_OWNER" >&2
  exit 1
fi

validate_catalog() {
  jq -e '.schemaVersion == 1 and (.restaurants | type == "array" and length > 0) and (.menuItems | type == "array" and length > 0)' \
    "$CATALOG_FILE" >/dev/null \
    || { echo "❌ Catalog phải có schemaVersion=1 và có restaurants/menuItems" >&2; exit 1; }

  jq -e '
    all(.restaurants[];
      (.restaurantKey | type == "string" and length > 0) and
      (.name | type == "string" and length > 0) and
      (.address | type == "string" and length > 0) and
      (.openingHour | type == "string") and
      (.closingHour | type == "string") and
      (.addressLat | type == "number") and
      (.addressLng | type == "number") and
      (.source.platform | . == "ShopeeFood" or . == "GrabFood") and
      (.source.url | startswith("https://")) and
      (.source.observedAt | type == "string" and test("^[0-9]{4}-[0-9]{2}-[0-9]{2}$"))
    )
  ' "$CATALOG_FILE" >/dev/null \
    || { echo "❌ Restaurant record thiếu field/provenance bắt buộc" >&2; exit 1; }

  jq -e '
    ([.restaurants[].restaurantKey] | unique | length) == (.restaurants | length) and
    (([.menuItems[].restaurantKey] - [.restaurants[].restaurantKey]) | length) == 0 and
    all(.menuItems[];
      (.restaurantKey | type == "string" and length > 0) and
      (.name | type == "string" and length > 0) and
      (.description | type == "string" and length > 0) and
      (.price | type == "number" and . > 0) and
      (.status | . == "AVAILABLE" or . == "SOLD_OUT" or . == "DISCONTINUED")
    )
  ' "$CATALOG_FILE" >/dev/null \
    || { echo "❌ Menu record hoặc quan hệ restaurantKey không hợp lệ" >&2; exit 1; }
}

print_summary() {
  jq -r '
    "dataset=" + .dataset,
    "city=" + .city,
    "restaurants=" + ((.restaurants | length) | tostring),
    "menuItems=" + ((.menuItems | length) | tostring),
    "sourcePlatforms=" + ([.restaurants[].source.platform] | unique | join(", ")),
    "sourceBackedFields=" + (.provenancePolicy.sourceBacked | join("; ")),
    "syntheticFields=" + (.provenancePolicy.synthetic | join("; "))
  ' "$CATALOG_FILE"
}

create_restaurant() {
  local row="$1"
  local payload response restaurant_id
  payload="$(jq -cn \
    --arg name "$(jq -r '.name' <<<"$row")" \
    --arg address "$(jq -r '.address' <<<"$row")" \
    --arg openingHour "$(jq -r '.openingHour' <<<"$row")" \
    --arg closingHour "$(jq -r '.closingHour' <<<"$row")" \
    --arg description "$(jq -r '.description' <<<"$row")" \
    --argjson addressLat "$(jq -r '.addressLat' <<<"$row")" \
    --argjson addressLng "$(jq -r '.addressLng' <<<"$row")" \
    '{name: $name, address: $address, openingHour: $openingHour,
      closingHour: $closingHour, description: $description,
      addressLat: $addressLat, addressLng: $addressLng}')"

  response="$(curl --fail-with-body --silent --show-error \
    --retry 3 --retry-all-errors --retry-max-time 90 \
    -X POST "$BASE/api/restaurants" \
    -H "Authorization: Bearer $OWNER_TOKEN" \
    -H 'Content-Type: application/json' \
    -d "$payload")"
  restaurant_id="$(jq -r '.id // .data.id // empty' <<<"$response")"
  [[ "$restaurant_id" =~ ^[0-9]+$ ]] \
    || { echo "❌ Không tạo được restaurant: $(jq -c '.' <<<"$response")" >&2; exit 1; }
  printf '%s\n' "$restaurant_id"
}

create_menu_item() {
  local restaurant_id="$1"
  local row="$2"
  local payload response menu_id desired_status
  desired_status="$(jq -r '.status' <<<"$row")"
  payload="$(jq -cn \
    --argjson restaurantId "$restaurant_id" \
    --arg name "$(jq -r '.name' <<<"$row")" \
    --arg description "$(jq -r '.description' <<<"$row")" \
    --argjson price "$(jq -r '.price' <<<"$row")" \
    '{restaurantId: $restaurantId, name: $name, description: $description,
      price: $price}')"

  response="$(curl --fail-with-body --silent --show-error \
    --retry 3 --retry-all-errors --retry-max-time 90 \
    -X POST "$BASE/api/menu-items" \
    -H "Authorization: Bearer $OWNER_TOKEN" \
    -H 'Content-Type: application/json' \
    -d "$payload")"
  menu_id="$(jq -r '.id // .data.id // empty' <<<"$response")"
  [[ "$menu_id" =~ ^[0-9]+$ ]] \
    || { echo "❌ Không tạo được menu item: $(jq -c '.' <<<"$response")" >&2; exit 1; }

  if [[ "$desired_status" != "AVAILABLE" ]]; then
    payload="$(jq -cn \
      --argjson restaurantId "$restaurant_id" \
      --arg name "$(jq -r '.name' <<<"$row")" \
      --arg description "$(jq -r '.description' <<<"$row")" \
      --argjson price "$(jq -r '.price' <<<"$row")" \
      --arg status "$desired_status" \
      '{restaurantId: $restaurantId, name: $name, description: $description,
        price: $price, status: $status}')"
    curl --fail-with-body --silent --show-error \
      --retry 3 --retry-all-errors --retry-max-time 90 \
      -X PUT "$BASE/api/menu-items/$menu_id" \
      -H "Authorization: Bearer $OWNER_TOKEN" \
      -H 'Content-Type: application/json' \
      -d "$payload" >/dev/null
  fi

  printf '%s\n' "$menu_id"
}

validate_catalog
restaurant_count="$(jq '.restaurants | length' "$CATALOG_FILE")"
menu_count="$(jq '.menuItems | length' "$CATALOG_FILE")"
echo "🌱 Realistic catalog fixture: $restaurant_count restaurants, $menu_count menu items"
print_summary

if [[ "$DRY_RUN" == "true" ]]; then
  echo "🔎 DRY_RUN=true — không gọi API, không tạo dữ liệu."
  jq -r '.restaurants[] | "- " + .restaurantKey + ": " + .name + " (" + .source.platform + ")"' "$CATALOG_FILE"
  exit 0
fi

echo "⚠️ DRY_RUN=false — sẽ tạo $restaurant_count restaurant và $menu_count menu item mới tại $BASE"
while IFS= read -r restaurant; do
  key="$(jq -r '.restaurantKey' <<<"$restaurant")"
  name="$(jq -r '.name' <<<"$restaurant")"
  restaurant_id="$(create_restaurant "$restaurant")"
  echo "✅ Restaurant $key id=$restaurant_id — $name"

  while IFS= read -r item; do
    item_name="$(jq -r '.name' <<<"$item")"
    item_id="$(create_menu_item "$restaurant_id" "$item")"
    echo "  ✅ Menu id=$item_id — $item_name"
  done < <(jq -c --arg key "$key" '.menuItems[] | select(.restaurantKey == $key)' "$CATALOG_FILE")
done < <(jq -c '.restaurants[]' "$CATALOG_FILE")

echo "🎉 Seed catalog xong. Rating/category/ETA trong sourceFacts là metadata tham chiếu;"
echo "   backend chỉ nhận restaurant/menu fields theo canonical API."
