#!/usr/bin/env bash
set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly CUSTOMER_DIR="$ROOT_DIR/delivery_app"
readonly WEB_DIR="$ROOT_DIR/delivery_web"
readonly SHIPPER_DIR="$ROOT_DIR/shipper_app2"
readonly BACKEND_DIR="$ROOT_DIR/backend_delivery"

for required in "$CUSTOMER_DIR/lib" "$WEB_DIR/src" "$SHIPPER_DIR/src" \
    "$BACKEND_DIR/scripts/verify-http-api-inventory.sh"; do
  if [[ ! -e "$required" ]]; then
    printf 'Missing required polyrepo surface: %s\n' "$required" >&2
    exit 1
  fi
done

assert_no_match() {
  local description="$1"
  local pattern="$2"
  shift 2
  local output status

  set +e
  output="$(rg -n --glob '!**/*.md' --glob '!**/*.g.dart' \
    --glob '!**/*.freezed.dart' --glob '!**/generated/**' \
    "$pattern" "$@" 2>&1)"
  status=$?
  set -e

  if (( status == 0 )); then
    printf '%s\n%s\n' "$description" "$output" >&2
    exit 1
  fi
  if (( status != 1 )); then
    printf 'Contract scan failed for %s:\n%s\n' "$description" "$output" >&2
    exit "$status"
  fi
}

readonly -a CLIENT_SOURCE=(
  "$CUSTOMER_DIR/lib"
  "$WEB_DIR/src"
  "$SHIPPER_DIR/src"
)

assert_no_match \
  'Client production source must not call backend service ports directly.' \
  'https?://(localhost|127\.0\.0\.1|10\.0\.2\.2):80(8|9)[0-9]' \
  "${CLIENT_SOURCE[@]}"

assert_no_match \
  'Client production source must not generate a duplicated /api/api prefix.' \
  '/api/api/' \
  "${CLIENT_SOURCE[@]}"

assert_no_match \
  'gRPC, STOMP, SockJS and legacy backend sockets are outside the MVP contract.' \
  '/ws-native|/ws/delivery-native|stomp_dart_client|@stomp/stompjs|sockjs-client|StompClient|SockJS|io\.grpc|package:grpc|grpc-' \
  "${CLIENT_SOURCE[@]}" \
  "$CUSTOMER_DIR/pubspec.yaml" "$WEB_DIR/package.json" "$SHIPPER_DIR/package.json"

assert_no_match \
  'Customer source must not call hidden shipper discovery, catalog helpers, chat, analytics, livestream or payment routes.' \
  '/shippers/\{id\}|/shippers/in-area|/restaurants/nearby|/restaurants/categories|/(chat|analytics|livestreams?|payments?)/' \
  "$CUSTOMER_DIR/lib"

assert_no_match \
  'Web source must not call nonexistent REST chat or hidden analytics/livestream/payment routes.' \
  '/api/(chat|analytics|livestream|payments?)/' \
  "$WEB_DIR/src"

assert_no_match \
  'Web MVP runtime/navigation must not expose unsupported Firebase Chat.' \
  'ChatProvider|ChatWidget|AdminChatPage|RestaurantChatPage|ADMIN_CHAT|RESTAURANT_CHAT' \
  "$WEB_DIR/src/App.tsx" \
  "$WEB_DIR/src/components/layouts/AdminLayout.tsx" \
  "$WEB_DIR/src/components/layout/RestaurantSidebar.tsx" \
  "$WEB_DIR/src/utils/constants.ts"

assert_no_match \
  'Web MVP package dependencies must not restore Firebase without Chat security authority.' \
  '"firebase"|"@firebase/' \
  "$WEB_DIR/package.json"

assert_no_match \
  'Shipper source must not call settlement/payment self-service or customer Order cancellation.' \
  '/api/(settlement|payments?)/|/api/orders/.*/cancel' \
  "$SHIPPER_DIR/src"

assert_no_match \
  'Shipper source must not restore hidden self-registration or placeholder/no-op screens.' \
  '/api/auth/register|Đây là màn hình cài đặt|Restart GPS|PermissionsRequest|SettingsScreen' \
  "$SHIPPER_DIR/src"

assert_no_match \
  'Order backend must not restore legacy update/delete/read/order-assign controllers or flags.' \
  'LegacyOrderMutationController|LegacyOrderReadController|ORDER_LEGACY_|app\.order\.legacy-' \
  "$BACKEND_DIR/order-service/src/main/java" \
  "$BACKEND_DIR/order-service/src/main/resources"

assert_no_match \
  'Removed shipper template, DI and legacy icon packages must not return.' \
  '@react-native/new-app-screen|inversify|reflect-metadata|react-native-vector-icons' \
  "$SHIPPER_DIR/package.json"

assert_no_match \
  'Removed Flutter native capabilities must not remain in direct dependencies.' \
  'agora_rtc_engine|in_app_purchase|socket_io_client|stomp_dart_client' \
  "$CUSTOMER_DIR/pubspec.yaml"

assert_no_match \
  'Client pagination must not depend on legacy Spring Page serialization.' \
  '\.content\b|\.number\b|totalElements\b' \
  "$CUSTOMER_DIR/lib/core/network/resources/page_dto.dart" \
  "$CUSTOMER_DIR/lib/features/orders/data" \
  "$CUSTOMER_DIR/lib/features/search/data" \
  "$WEB_DIR/src/services/api/adminService.ts" \
  "$WEB_DIR/src/services/api/contract.ts" \
  "$WEB_DIR/src/types/api.types.ts" \
  "$WEB_DIR/src/modules/restaurant/hooks/useOrder.tsx" \
  "$WEB_DIR/src/modules/restaurant/pages/RestaurantOrders.tsx" \
  "$WEB_DIR/src/modules/admin/pages/AdminOrdersPage.tsx" \
  "$WEB_DIR/src/modules/admin/pages/AdminShippersPage.tsx"

for removed_graph in \
    "$CUSTOMER_DIR/lib/features/livestream" \
    "$CUSTOMER_DIR/lib/features/iap" \
    "$CUSTOMER_DIR/lib/features/promotion" \
    "$CUSTOMER_DIR/lib/features/flash_sale" \
    "$CUSTOMER_DIR/lib/features/admin" \
    "$CUSTOMER_DIR/lib/features/location" \
    "$CUSTOMER_DIR/lib/core/config/agora_config.dart"; do
  if [[ -f "$removed_graph" ]] \
      || { [[ -d "$removed_graph" ]] \
        && [[ -n "$(find "$removed_graph" -type f -print -quit)" ]]; }; then
    printf 'Removed customer capability graph was restored: %s\n' "$removed_graph" >&2
    exit 1
  fi
done

for removed_graph in \
    "$WEB_DIR/src/modules/chat" \
    "$WEB_DIR/src/config/firebase.ts" \
    "$WEB_DIR/src/types/chat.types.ts"; do
  if [[ -f "$removed_graph" ]] \
      || { [[ -d "$removed_graph" ]] \
        && [[ -n "$(find "$removed_graph" -type f -print -quit)" ]]; }; then
    printf 'Removed web Chat/Firebase graph was restored: %s\n' "$removed_graph" >&2
    exit 1
  fi
done

bash "$BACKEND_DIR/scripts/verify-http-api-inventory.sh"

printf '%s\n' \
  'Polyrepo MVP contract gate passed: backend inventory aligned; client transports and hidden-route scans clean.'
