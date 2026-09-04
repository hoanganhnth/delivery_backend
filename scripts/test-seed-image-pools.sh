#!/usr/bin/env bash
# Verifies that the legacy image seed is backed by the Grab CDN catalog.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CATALOG_FILE="${CATALOG_FILE:-$WORKSPACE_DIR/../data/catalog/hanoi-catalog.json}"

output="$(DRY_RUN=true CATALOG_FILE="$CATALOG_FILE" bash "$SCRIPT_DIR/seed-legacy-fixture-images.sh")"

grep -Fq 'restaurantPoolSize=' <<<"$output"
grep -Fq 'menuPoolSize=' <<<"$output"
grep -Fq 'sampleRestaurantImage=https://huawei-food-cms.grab.com/' <<<"$output"
grep -Fq 'sampleMenuImage=https://huawei-food-cms.grab.com/' <<<"$output"
if grep -Fqi 'unsplash.com' <<<"$output"; then
  echo "Legacy image seed vẫn tham chiếu Unsplash" >&2
  exit 1
fi

restaurant_pool_size="$(awk -F= '$1 == "restaurantPoolSize" { print $2 }' <<<"$output")"
menu_pool_size="$(awk -F= '$1 == "menuPoolSize" { print $2 }' <<<"$output")"
[[ "$restaurant_pool_size" =~ ^[1-9][0-9]*$ && "$restaurant_pool_size" -ge 35 ]]
[[ "$menu_pool_size" =~ ^[1-9][0-9]*$ && "$menu_pool_size" -ge 35 ]]

echo "✅ Seed image pools use Grab CDN: restaurants=$restaurant_pool_size menus=$menu_pool_size"
