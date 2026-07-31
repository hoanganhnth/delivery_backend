#!/usr/bin/env bash
set -Eeuo pipefail

readonly BASE="${BASE:-http://127.0.0.1:8079}"
readonly FLOW_TIMEOUT_SECONDS="${FLOW_TIMEOUT_SECONDS:-240}"
readonly POLL_SECONDS="${POLL_SECONDS:-2}"
readonly PASS="${PASS:-Password123!}"
readonly RUN_ID="${RUN_ID:-$(date +%s)$RANDOM}"
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly BACKEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

COMPOSE=(docker compose -f "$BACKEND_DIR/docker-compose.yml")
if [[ -f "$BACKEND_DIR/docker-compose.secrets.yml" ]]; then
  COMPOSE+=( -f "$BACKEND_DIR/docker-compose.secrets.yml" )
fi

seed_result=""
response_file=""
admin_token=""
customer_token=""
customer_user_id=""
voucher_id=""
flash_item_id=""
campaign_id=""
rollout_started=false
cleanup_failed=false
reservation_services_safe_to_disable=false
order_checkout_disabled=false
declare -a fixture_order_ids=()

step() {
  printf '[Task 21] %s\n' "$1"
}

fail() {
  printf '[Task 21] ERROR: %s\n' "$1" >&2
  return 1
}

compose_enabled() {
  env \
    ORDER_VOUCHER_CHECKOUT_ENABLED=true \
    ORDER_FLASHSALE_CHECKOUT_ENABLED=true \
    PROMOTION_CHECKOUT_ENABLED=true \
    PROMOTION_OUTBOX_RELAY_ENABLED=true \
    PROMOTION_MERCHANT_CREATE_API_ENABLED=false \
    FLASHSALE_CHECKOUT_ENABLED=true \
    FLASHSALE_OUTBOX_RELAY_ENABLED=true \
    FLASHSALE_MERCHANT_REGISTRATION_ENABLED=false \
    "${COMPOSE[@]}" "$@"
}

compose_order_disabled() {
  env \
    ORDER_VOUCHER_CHECKOUT_ENABLED=false \
    ORDER_FLASHSALE_CHECKOUT_ENABLED=false \
    PROMOTION_CHECKOUT_ENABLED=true \
    PROMOTION_OUTBOX_RELAY_ENABLED=true \
    PROMOTION_MERCHANT_CREATE_API_ENABLED=false \
    FLASHSALE_CHECKOUT_ENABLED=true \
    FLASHSALE_OUTBOX_RELAY_ENABLED=true \
    FLASHSALE_MERCHANT_REGISTRATION_ENABLED=false \
    "${COMPOSE[@]}" "$@"
}

compose_disabled() {
  env \
    ORDER_VOUCHER_CHECKOUT_ENABLED=false \
    ORDER_FLASHSALE_CHECKOUT_ENABLED=false \
    PROMOTION_CHECKOUT_ENABLED=false \
    PROMOTION_OUTBOX_RELAY_ENABLED=false \
    PROMOTION_MERCHANT_CREATE_API_ENABLED=false \
    FLASHSALE_CHECKOUT_ENABLED=false \
    FLASHSALE_OUTBOX_RELAY_ENABLED=false \
    FLASHSALE_MERCHANT_REGISTRATION_ENABLED=false \
    "${COMPOSE[@]}" "$@"
}

psql_value() {
  local database="$1"
  local sql="$2"
  "${COMPOSE[@]}" exec -T postgres psql -U postgres -d "$database" -qAt -c "$sql"
}

wait_for_container() {
  local service="$1"
  local deadline=$((SECONDS + FLOW_TIMEOUT_SECONDS))
  local container_id status
  while (( SECONDS < deadline )); do
    container_id="$("${COMPOSE[@]}" ps -q "$service" 2>/dev/null || true)"
    if [[ -n "$container_id" ]]; then
      status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
        "$container_id" 2>/dev/null || true)"
      [[ "$status" == "healthy" || "$status" == "running" ]] && return 0
      [[ "$status" == "exited" || "$status" == "dead" ]] && break
    fi
    sleep "$POLL_SECONDS"
  done
  "${COMPOSE[@]}" logs --tail=120 "$service" >&2 || true
  fail "$service did not become healthy"
}

wait_sql_equals() {
  local database="$1"
  local sql="$2"
  local expected="$3"
  local label="$4"
  local deadline=$((SECONDS + FLOW_TIMEOUT_SECONDS))
  local actual=""
  while (( SECONDS < deadline )); do
    actual="$(psql_value "$database" "$sql" 2>/dev/null || true)"
    [[ "$actual" == "$expected" ]] && return 0
    sleep "$POLL_SECONDS"
  done
  fail "$label: expected '$expected', found '${actual:-<empty>}'"
}

decimal_equal() {
  awk -v left="$1" -v right="$2" \
    'BEGIN { difference = (left + 0) - (right + 0); if (difference < 0) difference = -difference; exit !(difference < 0.005) }'
}

login() {
  local email="$1"
  local device="$2"
  curl --fail-with-body --silent --show-error -X POST "$BASE/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"$PASS\",\"deviceId\":\"$device\",\"deviceName\":\"Task 21 runtime\",\"deviceType\":\"WEB\"}" \
    | jq -er '.accessToken // .data.accessToken'
}

api_post() {
  local path="$1"
  local token="$2"
  local payload="$3"
  curl --fail-with-body --silent --show-error -X POST "$BASE$path" \
    -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    -d "$payload"
}

expect_post_failure() {
  local path="$1"
  local token="$2"
  local payload="$3"
  local label="$4"
  local status
  : > "$response_file"
  status="$(curl --silent --show-error -o "$response_file" -w '%{http_code}' \
    -X POST "$BASE$path" -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' -d "$payload" || true)"
  if [[ "$status" =~ ^2 ]]; then
    sed -n '1,80p' "$response_file" >&2
    fail "$label unexpectedly succeeded with HTTP $status"
  fi
  [[ "$status" =~ ^[45][0-9][0-9]$ ]] || fail "$label returned invalid HTTP status '${status:-<empty>}'"
}

create_order() {
  local payload="$1"
  local response order_id
  response="$(api_post '/api/orders' "$customer_token" "$payload")"
  order_id="$(jq -er '.data.id // .id' <<<"$response")"
  [[ "$order_id" =~ ^[0-9]+$ ]] || fail "order response did not contain a numeric id"
  fixture_order_ids+=("$order_id")
  printf '%s\n' "$response"
}

cancel_order() {
  local order_id="$1"
  local reason="$2"
  curl --fail-with-body --silent --show-error -X PUT "$BASE/api/orders/$order_id/cancel" \
    -H "Authorization: Bearer $customer_token" -H 'Content-Type: application/json' \
    -d "{\"reason\":\"$reason\"}" >/dev/null
}

replay_order_event() {
  local order_id="$1"
  local topic="$2"
  local event_type="$3"
  local row key payload
  row="$(psql_value order_db "SELECT event_key || '|' || payload FROM outbox_events
    WHERE aggregate_id = '$order_id' AND topic = '$topic' AND event_type = '$event_type'
    ORDER BY id DESC LIMIT 1;")"
  [[ "$row" == *'|'* ]] || fail "missing $topic outbox payload for order $order_id"
  key="${row%%|*}"
  payload="${row#*|}"
  printf '%s:%s\n' "$key" "$payload" | "${COMPOSE[@]}" exec -T kafka \
    kafka-console-producer --bootstrap-server kafka:9092 --topic "$topic" \
    --property parse.key=true --property key.separator=: >/dev/null
}

assert_order_matches_preview() {
  local preview="$1"
  local order="$2"
  local label="$3"
  local preview_subtotal preview_discount preview_shipping preview_total
  local order_subtotal order_discount order_shipping order_total
  preview_subtotal="$(jq -er '.data.subtotal // .subtotal' <<<"$preview")"
  preview_discount="$(jq -er '.data.discountAmount // .discountAmount' <<<"$preview")"
  preview_shipping="$(jq -er '.data.shippingFee // .shippingFee' <<<"$preview")"
  preview_total="$(jq -er '.data.totalPrice // .totalPrice' <<<"$preview")"
  order_subtotal="$(jq -er '.data.subtotalPrice // .subtotalPrice' <<<"$order")"
  order_discount="$(jq -er '.data.discountAmount // .discountAmount' <<<"$order")"
  order_shipping="$(jq -er '.data.shippingFee // .shippingFee' <<<"$order")"
  order_total="$(jq -er '.data.totalPrice // .totalPrice' <<<"$order")"
  decimal_equal "$preview_subtotal" "$order_subtotal" || fail "$label subtotal diverged"
  decimal_equal "$preview_discount" "$order_discount" || fail "$label discount diverged"
  decimal_equal "$preview_shipping" "$order_shipping" || fail "$label shipping fee diverged"
  decimal_equal "$preview_total" "$order_total" || fail "$label total diverged"
}

cancel_unfinished_fixture_orders() {
  local order_id status
  [[ -n "$customer_token" ]] || return 0
  for order_id in "${fixture_order_ids[@]}"; do
    status="$(psql_value order_db "SELECT status FROM orders WHERE id = $order_id;" 2>/dev/null || true)"
    case "$status" in
      PENDING|CONFIRMED|FINDING_SHIPPER|WAIT_SHIPPER_CONFIRM|ASSIGNED)
        curl --silent --show-error -X PUT "$BASE/api/orders/$order_id/cancel" \
          -H "Authorization: Bearer $customer_token" -H 'Content-Type: application/json' \
          -d "{\"reason\":\"Task 21 runtime cleanup\"}" >/dev/null || cleanup_failed=true
        ;;
    esac
  done
}

release_fixture_reservations_directly() {
  local internal_secret row reservation_id order_id order_status
  [[ -f "$BACKEND_DIR/.secrets/internal-secret" ]] || return 0
  internal_secret="$(<"$BACKEND_DIR/.secrets/internal-secret")"

  if [[ "$voucher_id" =~ ^[0-9]+$ ]]; then
    while IFS='|' read -r reservation_id order_id; do
      [[ -n "$reservation_id" && "$order_id" =~ ^[0-9]+$ ]] || continue
      order_status="$(psql_value order_db "SELECT status FROM orders WHERE id = $order_id;" 2>/dev/null || true)"
      [[ "$order_status" == "DELIVERED" ]] && continue
      "${COMPOSE[@]}" exec -T promotion-service wget -q -O /dev/null --post-data='' \
        --header="Internal-Token: $internal_secret" \
        "http://localhost:8096/api/promotions/reservations/$reservation_id/release?orderId=$order_id" \
        || cleanup_failed=true
    done < <(psql_value promotion_db "SELECT reservation_id, order_id FROM voucher_reservations
      WHERE voucher_id = $voucher_id AND state IN ('RESERVED','COMMITTED');" | tr '\t' '|')
  fi

  if [[ "$flash_item_id" =~ ^[0-9]+$ ]]; then
    while IFS='|' read -r reservation_id order_id; do
      [[ -n "$reservation_id" && "$order_id" =~ ^[0-9]+$ ]] || continue
      order_status="$(psql_value order_db "SELECT status FROM orders WHERE id = $order_id;" 2>/dev/null || true)"
      [[ "$order_status" == "DELIVERED" ]] && continue
      "${COMPOSE[@]}" exec -T flashsale-service wget -q -O /dev/null --post-data='' \
        --header="Internal-Token: $internal_secret" \
        "http://localhost:8092/api/flashsales/internal/reservations/$reservation_id/release?orderId=$order_id" \
        || cleanup_failed=true
    done < <(psql_value flashsale_db "SELECT r.reservation_id, r.order_id
      FROM flash_sale_reservations r JOIN flash_sale_reservation_lines l
        ON l.reservation_id = r.reservation_id
      WHERE l.flash_sale_item_id = $flash_item_id AND r.state IN ('RESERVED','COMMITTED');" | tr '\t' '|')
  fi
}

wait_for_outbox_drain() {
  local deadline=$((SECONDS + FLOW_TIMEOUT_SECONDS))
  local promotion_pending flash_pending
  while (( SECONDS < deadline )); do
    promotion_pending="$(psql_value promotion_db \
      "SELECT count(*) FROM promotion_outbox_events WHERE status IN ('PENDING','DEAD');" 2>/dev/null || true)"
    flash_pending="$(psql_value flashsale_db \
      "SELECT count(*) FROM flash_sale_outbox_events WHERE status IN ('PENDING','DEAD');" 2>/dev/null || true)"
    [[ "$promotion_pending" == "0" && "$flash_pending" == "0" ]] && return 0
    sleep "$POLL_SECONDS"
  done
  cleanup_failed=true
  printf '[Task 21] ERROR: reservation outboxes did not drain (promotion=%s, flash=%s)\n' \
    "${promotion_pending:-unknown}" "${flash_pending:-unknown}" >&2
  return 1
}

fixture_reservations_are_terminal() {
  local active order_id order_status
  if [[ "$voucher_id" =~ ^[0-9]+$ ]]; then
    active="$(psql_value promotion_db "SELECT count(*) FROM voucher_reservations
      WHERE voucher_id = $voucher_id AND state IN ('RESERVED','COMMITTED');" 2>/dev/null || true)"
    [[ "$active" == "0" ]] || return 1
  fi

  if [[ "$flash_item_id" =~ ^[0-9]+$ ]]; then
    active="$(psql_value flashsale_db "SELECT count(*) FROM flash_sale_reservations r
      JOIN flash_sale_reservation_lines l ON l.reservation_id = r.reservation_id
      WHERE l.flash_sale_item_id = $flash_item_id AND r.state = 'RESERVED';" 2>/dev/null || true)"
    [[ "$active" == "0" ]] || return 1
    while IFS= read -r order_id; do
      [[ "$order_id" =~ ^[0-9]+$ ]] || return 1
      order_status="$(psql_value order_db "SELECT status FROM orders WHERE id = $order_id;" 2>/dev/null || true)"
      [[ "$order_status" == "DELIVERED" ]] || return 1
    done < <(psql_value flashsale_db "SELECT DISTINCT r.order_id
      FROM flash_sale_reservations r JOIN flash_sale_reservation_lines l
        ON l.reservation_id = r.reservation_id
      WHERE l.flash_sale_item_id = $flash_item_id AND r.state = 'COMMITTED';")
  fi
  return 0
}

assert_flags_disabled() {
  local service variable expected
  while IFS='|' read -r service variable expected; do
    docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$service" \
      | grep -qx "$variable=$expected" || {
        printf '[Task 21] ERROR: %s does not expose %s=%s after rollback\n' \
          "$service" "$variable" "$expected" >&2
        cleanup_failed=true
      }
  done <<'EOF'
order-service|ORDER_VOUCHER_CHECKOUT_ENABLED|false
order-service|ORDER_FLASHSALE_CHECKOUT_ENABLED|false
promotion-service|PROMOTION_CHECKOUT_ENABLED|false
promotion-service|PROMOTION_OUTBOX_RELAY_ENABLED|false
flashsale-service|FLASHSALE_CHECKOUT_ENABLED|false
flashsale-service|FLASHSALE_OUTBOX_RELAY_ENABLED|false
EOF
}

order_flags_are_disabled() {
  local environment
  environment="$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' order-service 2>/dev/null)"
  grep -qx 'ORDER_VOUCHER_CHECKOUT_ENABLED=false' <<<"$environment" \
    && grep -qx 'ORDER_FLASHSALE_CHECKOUT_ENABLED=false' <<<"$environment"
}

cleanup() {
  local original_status=$?
  trap - EXIT
  set +e
  rm -f "${seed_result:-}" "${response_file:-}"
  if [[ "$rollout_started" == "true" ]]; then
    step "rollback: disable Order selection before draining reservations"
    if compose_order_disabled up -d --no-deps --force-recreate order-service >/dev/null \
        && wait_for_container order-service && order_flags_are_disabled; then
      order_checkout_disabled=true
    else
      cleanup_failed=true
      printf '%s\n' '[Task 21] ERROR: could not prove Order checkout flags are disabled.' >&2
    fi
    cancel_unfinished_fixture_orders
    sleep "$POLL_SECONDS"
    release_fixture_reservations_directly
    if fixture_reservations_are_terminal && wait_for_outbox_drain; then
      reservation_services_safe_to_disable=true
    else
      cleanup_failed=true
      if [[ "$order_checkout_disabled" == "true" ]]; then
        printf '%s\n' \
          '[Task 21] ERROR: keeping Promotion/Flash checkout + relays enabled for recovery; Order checkout is disabled.' >&2
      else
        printf '%s\n' \
          '[Task 21] ERROR: reservation recovery remains enabled and Order flag state requires immediate operator verification.' >&2
      fi
    fi

    if [[ "$voucher_id" =~ ^[0-9]+$ ]]; then
      psql_value promotion_db "UPDATE vouchers SET active = false, updated_at = CURRENT_TIMESTAMP
        WHERE id = $voucher_id RETURNING id;" >/dev/null || cleanup_failed=true
    fi
    if [[ "$campaign_id" =~ ^[0-9]+$ ]]; then
      psql_value flashsale_db "UPDATE flash_sale_campaigns SET status = 'ENDED', updated_at = CURRENT_TIMESTAMP
        WHERE id = $campaign_id RETURNING id;" >/dev/null || cleanup_failed=true
    fi

    if [[ "$reservation_services_safe_to_disable" == "true" ]]; then
      step "rollback: disable reservation relays and service checkout"
      compose_disabled up -d --no-deps --force-recreate \
        order-service promotion-service flashsale-service >/dev/null || cleanup_failed=true
      wait_for_container order-service || cleanup_failed=true
      wait_for_container promotion-service || cleanup_failed=true
      wait_for_container flashsale-service || cleanup_failed=true
      assert_flags_disabled
    fi
  fi
  if [[ "$cleanup_failed" == "true" && "$original_status" -eq 0 ]]; then
    original_status=1
  fi
  exit "$original_status"
}
trap cleanup EXIT

command -v curl >/dev/null
command -v jq >/dev/null
command -v docker >/dev/null
command -v awk >/dev/null
[[ "$RUN_ID" =~ ^[0-9]{1,30}$ ]] || fail "RUN_ID must contain 1-30 digits"
docker info >/dev/null 2>&1 || fail "Docker daemon is unavailable"

cd "$BACKEND_DIR"
seed_result="$(mktemp)"
response_file="$(mktemp)"

step "build and start Order/Promotion/Flash-sale with checkout + relays enabled"
"${COMPOSE[@]}" build order-service promotion-service flashsale-service >/dev/null
rollout_started=true
compose_enabled up -d --no-deps --force-recreate \
  order-service promotion-service flashsale-service >/dev/null
wait_for_container order-service
wait_for_container promotion-service
wait_for_container flashsale-service

step "verify Task 21 migrations"
wait_sql_equals order_db \
  "SELECT count(*) FROM flyway_schema_history WHERE version = '8' AND success;" "1" "Order V8"
wait_sql_equals promotion_db \
  "SELECT count(*) FROM flyway_schema_history WHERE version IN ('2','3') AND success;" "2" "Promotion V2/V3"
wait_sql_equals flashsale_db \
  "SELECT count(*) FROM flyway_schema_history WHERE version IN ('2','3') AND success;" "2" "Flash-sale V2/V3"

step "seed unique customer, restaurant, menu and shipper actors"
env RUN_ID="$RUN_ID" SEED_OUTPUT_FILE="$seed_result" BASE="$BASE" \
  bash scripts/seed.sh >/dev/null
customer_token="$(jq -er '.customerToken' "$seed_result")"
owner_token="$(jq -er '.ownerToken' "$seed_result")"
shipper_token="$(jq -er '.shipperToken' "$seed_result")"
shipper_user_id="$(jq -er '.shipperUserId' "$seed_result")"
restaurant_id="$(jq -er '.restaurantId' "$seed_result")"
menu_item_id="$(jq -er '.menuItemId' "$seed_result")"
customer_email="customer+$RUN_ID@test.dev"
customer_user_id="$(psql_value auth_db \
  "SELECT user_id FROM auth_account WHERE email = '$customer_email';")"
[[ "$customer_user_id" =~ ^[0-9]+$ ]] || fail "missing canonical customer user id"

step "provision an operator-owned ADMIN and create authoritative voucher/campaign"
admin_email="admin+$RUN_ID@test.dev"
safe_run_id="${RUN_ID//[^a-zA-Z0-9_.-]/-}"
"${COMPOSE[@]}" run --rm --no-deps --name "auth-admin-task21-$safe_run_id" \
  -e APP_OPERATOR_ADMIN_PROVISIONING_ENABLED=true \
  -e APP_OPERATOR_ADMIN_PROVISIONING_EMAIL="$admin_email" \
  -e APP_OPERATOR_ADMIN_PROVISIONING_PASSWORD="$PASS" \
  -e APP_OPERATOR_ADMIN_PROVISIONING_EXIT_AFTER_RUN=true \
  auth-service >/dev/null
admin_token="$(login "$admin_email" "task21-$RUN_ID-admin")"

voucher_code="T21V$RUN_ID"
voucher_response="$(api_post '/api/promotions/platform' "$admin_token" \
  "{\"code\":\"$voucher_code\",\"name\":\"Task 21 voucher $RUN_ID\",\"description\":\"Runtime proof\",\"rewardType\":\"FIXED\",\"discountValue\":10000,\"maxDiscountValue\":10000,\"scopeType\":\"SHOP\",\"scopeRefId\":$restaurant_id,\"totalQuantity\":5,\"usageLimitPerUser\":1,\"startTime\":\"2000-01-01T00:00:00\",\"endTime\":\"2099-01-01T00:00:00\",\"minOrderValue\":0}")"
voucher_id="$(jq -er '.data.id // .id' <<<"$voucher_response")"
jq -e --argjson restaurantId "$restaurant_id" \
  '(.data.creatorType // .creatorType) == "PLATFORM"
   and (.data.scopeType // .scopeType) == "SHOP"
   and (.data.scopeRefId // .scopeRefId) == $restaurantId' \
  <<<"$voucher_response" >/dev/null || fail "voucher authority response is invalid"

campaign_response="$(api_post '/api/flashsales/admin/campaigns' "$admin_token" \
  "{\"name\":\"Task 21 campaign $RUN_ID\",\"isRecurring\":false,\"startTime\":\"00:00:00\",\"endTime\":\"23:59:59\"}")"
campaign_id="$(jq -er '.data.id // .id' <<<"$campaign_response")"
curl --fail-with-body --silent --show-error -X PUT \
  "$BASE/api/flashsales/admin/campaigns/$campaign_id/status?status=ACTIVE" \
  -H "Authorization: Bearer $admin_token" >/dev/null

# Merchant registration intentionally remains unavailable. Seed the one valid,
# restaurant-owned PENDING row, then exercise ADMIN approval through Gateway.
flash_item_id="$("${COMPOSE[@]}" exec -T postgres psql -U postgres -d flashsale_db -qAt \
  -v campaign_id="$campaign_id" -v restaurant_id="$restaurant_id" -v menu_item_id="$menu_item_id" \
  -c "INSERT INTO flash_sale_items
    (campaign_id, restaurant_id, menu_item_id, original_price, flash_sale_price,
     stock_quantity, sold_quantity, status, created_at, updated_at)
    VALUES (:campaign_id, :restaurant_id, :menu_item_id, 45000, 30000, 1, 0,
      'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id;")"
[[ "$flash_item_id" =~ ^[0-9]+$ ]] || fail "failed to seed flash-sale item"
curl --fail-with-body --silent --show-error -X PUT \
  "$BASE/api/flashsales/admin/items/$flash_item_id/approve" \
  -H "Authorization: Bearer $admin_token" >/dev/null

step "verify Gateway exposes wallet/public catalog but not merchant/internal reserve"
api_post "/api/promotions/collect/$voucher_code" "$customer_token" '{}' >/dev/null
wallet="$(curl --fail-with-body --silent --show-error "$BASE/api/promotions/my-vouchers" \
  -H "Authorization: Bearer $customer_token")"
jq -e --argjson voucherId "$voucher_id" \
  'any((.data // .)[]; .id == $voucherId)' <<<"$wallet" >/dev/null \
  || fail "voucher wallet did not expose collected voucher"
catalog="$(curl --fail-with-body --silent --show-error \
  "$BASE/api/flashsales/public/campaigns/$campaign_id/items")"
jq -e --argjson flashItemId "$flash_item_id" --argjson menuItemId "$menu_item_id" \
  'any((.data // .)[]; .id == $flashItemId and .menuItemId == $menuItemId
    and .flashSalePrice == 30000 and .status == "APPROVED")' <<<"$catalog" >/dev/null \
  || fail "public flash catalog did not expose approved canonical item"
expect_post_failure '/api/flashsales/merchant/items' "$owner_token" \
  "{\"campaignId\":$campaign_id,\"restaurantId\":$restaurant_id,\"menuItemId\":$menu_item_id,\"originalPrice\":45000,\"flashSalePrice\":30000,\"stockQuantity\":1}" \
  "merchant registration boundary"
expect_post_failure '/api/promotions/reserve' "$customer_token" '{}' \
  "internal voucher reserve boundary"

normal_preview_payload="{\"restaurantId\":$restaurant_id,\"deliveryLat\":10.7740,\"deliveryLng\":106.7040,\"items\":[{\"menuItemId\":$menu_item_id,\"quantity\":1}]}"
normal_order_payload="{\"restaurantId\":$restaurant_id,\"deliveryAddress\":\"456 Nguyen Hue, Q1\",\"deliveryLat\":10.7740,\"deliveryLng\":106.7040,\"customerName\":\"Task 21 Customer\",\"customerPhone\":\"0900000001\",\"paymentMethod\":\"COD\",\"items\":[{\"menuItemId\":$menu_item_id,\"quantity\":1}]}"

step "normal checkout remains canonical while rollout flags are enabled"
normal_preview="$(api_post '/api/orders/checkout-preview' "$customer_token" "$normal_preview_payload")"
normal_order="$(create_order "$normal_order_payload")"
normal_order_id="$(jq -er '.data.id // .id' <<<"$normal_order")"
fixture_order_ids+=("$normal_order_id")
assert_order_matches_preview "$normal_preview" "$normal_order" "normal checkout"
cancel_order "$normal_order_id" "Task 21 normal checkout proof"

step "voucher preview/create use the same server-owned totals"
voucher_preview_payload="{\"restaurantId\":$restaurant_id,\"deliveryLat\":10.7740,\"deliveryLng\":106.7040,\"voucherId\":$voucher_id,\"items\":[{\"menuItemId\":$menu_item_id,\"quantity\":1}]}"
voucher_order_payload="{\"restaurantId\":$restaurant_id,\"deliveryAddress\":\"456 Nguyen Hue, Q1\",\"deliveryLat\":10.7740,\"deliveryLng\":106.7040,\"customerName\":\"Task 21 Customer\",\"customerPhone\":\"0900000001\",\"paymentMethod\":\"COD\",\"voucherIds\":[$voucher_id],\"items\":[{\"menuItemId\":$menu_item_id,\"quantity\":1}]}"
voucher_preview="$(api_post '/api/orders/checkout-preview' "$customer_token" "$voucher_preview_payload")"
voucher_order="$(create_order "$voucher_order_payload")"
voucher_order_id="$(jq -er '.data.id // .id' <<<"$voucher_order")"
fixture_order_ids+=("$voucher_order_id")
assert_order_matches_preview "$voucher_preview" "$voucher_order" "voucher checkout"
wait_sql_equals promotion_db \
  "SELECT state FROM voucher_reservations WHERE order_id = $voucher_order_id;" \
  "COMMITTED" "voucher reservation commit"
wait_sql_equals promotion_db \
  "SELECT used_quantity FROM vouchers WHERE id = $voucher_id;" "1" "voucher usage counter"

step "exact order.created replay is idempotent and duplicate voucher use fails"
replay_order_event "$voucher_order_id" order.created ORDER_CREATED
sleep "$POLL_SECONDS"
wait_sql_equals promotion_db \
  "SELECT state || '|' || (SELECT used_quantity FROM vouchers WHERE id = $voucher_id)
   FROM voucher_reservations WHERE order_id = $voucher_order_id;" \
  "COMMITTED|1" "voucher created replay"
order_count_before="$(psql_value order_db "SELECT count(*) FROM orders WHERE user_id = $customer_user_id;")"
expect_post_failure '/api/orders' "$customer_token" "$voucher_order_payload" "duplicate voucher checkout"
order_count_after="$(psql_value order_db "SELECT count(*) FROM orders WHERE user_id = $customer_user_id;")"
[[ "$order_count_after" == "$order_count_before" ]] || fail "duplicate voucher created a partial order"

step "voucher cancellation releases wallet/counter and replay stays terminal"
cancel_order "$voucher_order_id" "Task 21 voucher release proof"
wait_sql_equals promotion_db \
  "SELECT r.state || '|' || uv.status || '|' || v.used_quantity
   FROM voucher_reservations r
   JOIN user_vouchers uv ON uv.user_id = r.user_id AND uv.voucher_id = r.voucher_id
   JOIN vouchers v ON v.id = r.voucher_id
   WHERE r.order_id = $voucher_order_id;" \
  "RELEASED|SAVED|0" "voucher cancellation release"
replay_order_event "$voucher_order_id" order.cancelled ORDER_CANCELLED
sleep "$POLL_SECONDS"
wait_sql_equals promotion_db \
  "SELECT r.state || '|' || v.used_quantity FROM voucher_reservations r
   JOIN vouchers v ON v.id = r.voucher_id WHERE r.order_id = $voucher_order_id;" \
  "RELEASED|0" "voucher cancelled replay"

flash_preview_payload="{\"restaurantId\":$restaurant_id,\"deliveryLat\":10.7740,\"deliveryLng\":106.7040,\"items\":[{\"menuItemId\":$menu_item_id,\"flashSaleItemId\":$flash_item_id,\"quantity\":1}]}"
flash_order_payload="{\"restaurantId\":$restaurant_id,\"deliveryAddress\":\"456 Nguyen Hue, Q1\",\"deliveryLat\":10.7740,\"deliveryLng\":106.7040,\"customerName\":\"Task 21 Customer\",\"customerPhone\":\"0900000001\",\"paymentMethod\":\"COD\",\"items\":[{\"menuItemId\":$menu_item_id,\"flashSaleItemId\":$flash_item_id,\"quantity\":1}]}"

step "flash preview/create use authoritative campaign price"
flash_preview="$(api_post '/api/orders/checkout-preview' "$customer_token" "$flash_preview_payload")"
flash_order="$(create_order "$flash_order_payload")"
flash_order_id="$(jq -er '.data.id // .id' <<<"$flash_order")"
fixture_order_ids+=("$flash_order_id")
assert_order_matches_preview "$flash_preview" "$flash_order" "flash checkout"
flash_preview_subtotal="$(jq -er '.data.subtotal // .subtotal' <<<"$flash_preview")"
decimal_equal "$flash_preview_subtotal" "30000" || fail "flash preview did not use canonical 30000 price"
wait_sql_equals flashsale_db \
  "SELECT state FROM flash_sale_reservations WHERE order_id = $flash_order_id;" \
  "COMMITTED" "flash reservation commit"
wait_sql_equals flashsale_db \
  "SELECT sold_quantity FROM flash_sale_items WHERE id = $flash_item_id;" "1" "flash sold counter"

step "exact replay is idempotent and exhausted stock creates no partial order"
replay_order_event "$flash_order_id" order.created ORDER_CREATED
sleep "$POLL_SECONDS"
wait_sql_equals flashsale_db \
  "SELECT r.state || '|' || i.sold_quantity FROM flash_sale_reservations r
   JOIN flash_sale_reservation_lines l ON l.reservation_id = r.reservation_id
   JOIN flash_sale_items i ON i.id = l.flash_sale_item_id
   WHERE r.order_id = $flash_order_id;" \
  "COMMITTED|1" "flash created replay"
order_count_before="$(psql_value order_db "SELECT count(*) FROM orders WHERE user_id = $customer_user_id;")"
expect_post_failure '/api/orders' "$customer_token" "$flash_order_payload" "exhausted flash stock"
order_count_after="$(psql_value order_db "SELECT count(*) FROM orders WHERE user_id = $customer_user_id;")"
[[ "$order_count_after" == "$order_count_before" ]] || fail "exhausted flash stock created a partial order"
wait_sql_equals flashsale_db \
  "SELECT sold_quantity FROM flash_sale_items WHERE id = $flash_item_id;" "1" "flash stock after failure"

step "flash cancellation releases stock and exact replay stays terminal"
cancel_order "$flash_order_id" "Task 21 flash release proof"
wait_sql_equals flashsale_db \
  "SELECT r.state || '|' || i.sold_quantity FROM flash_sale_reservations r
   JOIN flash_sale_reservation_lines l ON l.reservation_id = r.reservation_id
   JOIN flash_sale_items i ON i.id = l.flash_sale_item_id
   WHERE r.order_id = $flash_order_id;" \
  "RELEASED|0" "flash cancellation release"
replay_order_event "$flash_order_id" order.cancelled ORDER_CANCELLED
sleep "$POLL_SECONDS"
wait_sql_equals flashsale_db \
  "SELECT r.state || '|' || i.sold_quantity FROM flash_sale_reservations r
   JOIN flash_sale_reservation_lines l ON l.reservation_id = r.reservation_id
   JOIN flash_sale_items i ON i.id = l.flash_sale_item_id
   WHERE r.order_id = $flash_order_id;" \
  "RELEASED|0" "flash cancelled replay"

step "voucher + flash no-stacking request fails before any reservation/order mutation"
stacked_payload="{\"restaurantId\":$restaurant_id,\"deliveryAddress\":\"456 Nguyen Hue, Q1\",\"deliveryLat\":10.7740,\"deliveryLng\":106.7040,\"customerName\":\"Task 21 Customer\",\"customerPhone\":\"0900000001\",\"paymentMethod\":\"COD\",\"voucherIds\":[$voucher_id],\"items\":[{\"menuItemId\":$menu_item_id,\"flashSaleItemId\":$flash_item_id,\"quantity\":1}]}"
order_count_before="$(psql_value order_db "SELECT count(*) FROM orders WHERE user_id = $customer_user_id;")"
expect_post_failure '/api/orders' "$customer_token" "$stacked_payload" "voucher/flash stacking"
order_count_after="$(psql_value order_db "SELECT count(*) FROM orders WHERE user_id = $customer_user_id;")"
[[ "$order_count_after" == "$order_count_before" ]] || fail "stacking rejection created a partial order"

step "create a second flash order and complete Order -> Delivery -> Settlement"
settlement_order="$(create_order "$flash_order_payload")"
settlement_order_id="$(jq -er '.data.id // .id' <<<"$settlement_order")"
fixture_order_ids+=("$settlement_order_id")
wait_sql_equals flashsale_db \
  "SELECT state FROM flash_sale_reservations WHERE order_id = $settlement_order_id;" \
  "COMMITTED" "settlement flash reservation commit"
curl --fail-with-body --silent --show-error -X POST \
  "$BASE/api/restaurants/orders/$settlement_order_id/confirm" \
  -H "Authorization: Bearer $owner_token" -H 'Content-Type: application/json' \
  -d "{\"restaurantId\":$restaurant_id,\"estimatedPrepTime\":15}" >/dev/null

deadline=$((SECONDS + FLOW_TIMEOUT_SECONDS))
delivery_id=""
while (( SECONDS < deadline )); do
  notifications="$(curl --silent --show-error "$BASE/api/notifications/unread" \
    -H "Authorization: Bearer $shipper_token" || true)"
  notification="$(jq -cer --argjson orderId "$settlement_order_id" \
    '[.data[]? | select(.type == "MATCH_FOUND" and .relatedEntityId == $orderId
      and .relatedEntityType == "ORDER")][0] // empty' <<<"$notifications" 2>/dev/null || true)"
  if [[ -n "$notification" ]]; then
    offer="$(curl --silent --show-error "$BASE/api/deliveries/offers/current" \
      -H "Authorization: Bearer $shipper_token" || true)"
    offer_order_id="$(jq -r '.data.orderId // empty' <<<"$offer" 2>/dev/null || true)"
    offer_status="$(jq -r '.data.status // empty' <<<"$offer" 2>/dev/null || true)"
    delivery_id="$(jq -r '.data.deliveryId // empty' <<<"$offer" 2>/dev/null || true)"
    if [[ "$offer_order_id" == "$settlement_order_id" \
        && "$offer_status" == "WAIT_SHIPPER_CONFIRM" \
        && "$delivery_id" =~ ^[0-9]+$ ]]; then
      break
    fi
  fi
  sleep "$POLL_SECONDS"
done
[[ "$delivery_id" =~ ^[0-9]+$ ]] || fail "timed out waiting for shipper offer"

curl --fail-with-body --silent --show-error -X POST "$BASE/api/deliveries/accept" \
  -H "Authorization: Bearer $shipper_token" -H 'Content-Type: application/json' \
  -d "{\"orderId\":$settlement_order_id,\"action\":\"ACCEPT\",\"currentLat\":10.7780,\"currentLng\":106.7020}" >/dev/null
for status in PICKED_UP DELIVERING DELIVERED; do
  curl --fail-with-body --silent --show-error -X PUT \
    "$BASE/api/deliveries/$delivery_id/status?status=$status" \
    -H "Authorization: Bearer $shipper_token" >/dev/null
done
wait_sql_equals order_db \
  "SELECT status FROM orders WHERE id = $settlement_order_id;" "DELIVERED" "delivered Order state"
wait_sql_equals settlement_db \
  "SELECT count(*) FROM transactions WHERE order_id = $settlement_order_id;" "4" "settlement ledger"

step "reconcile immutable Order total, delivery event and COD ledger debit"
order_total="$(psql_value order_db \
  "SELECT total_price FROM orders WHERE id = $settlement_order_id;")"
delivery_payload="$(psql_value delivery_db \
  "SELECT payload FROM outbox_events WHERE aggregate_id = '$delivery_id'
   AND topic = 'delivery.completed' ORDER BY id DESC LIMIT 1;")"
[[ -n "$delivery_payload" ]] || fail "missing delivery.completed payload"
jq -e \
  '(.totalPrice == (.restaurantEarnings + .restaurantCommission + .shippingFee))
   and (.shippingFee == (.shipperEarnings + .shippingCommission))
   and (.totalPlatformEarnings == (.restaurantCommission + .shippingCommission))' \
  <<<"$delivery_payload" >/dev/null || fail "delivery.completed amount components do not reconcile"
delivery_total="$(jq -er '.totalPrice' <<<"$delivery_payload")"
cod_debit="$(psql_value settlement_db \
  "SELECT amount FROM transactions WHERE order_id = $settlement_order_id
   AND reason = 'COD_SETTLEMENT' AND direction = 'DEBIT';")"
decimal_equal "$order_total" "$delivery_total" || fail "Order total diverged from delivery.completed total"
decimal_equal "$order_total" "$cod_debit" || fail "Order total diverged from COD settlement debit"

step "replay delivery.completed and prove settlement receipt/ledger idempotency"
printf '%s:%s\n' "$delivery_id" "$delivery_payload" | "${COMPOSE[@]}" exec -T kafka \
  kafka-console-producer --bootstrap-server kafka:9092 --topic delivery.completed \
  --property parse.key=true --property key.separator=: >/dev/null
sleep 5
ledger_count="$(psql_value settlement_db \
  "SELECT count(*) FROM transactions WHERE order_id = $settlement_order_id;")"
receipt_count="$(psql_value settlement_db \
  "SELECT count(*) FROM settlement_receipts WHERE order_id = $settlement_order_id;")"
[[ "$ledger_count" == "4" && "$receipt_count" == "1" ]] \
  || fail "settlement replay changed durable ledger/receipt cardinality"

step "take runtime shipper offline"
curl --fail-with-body --silent --show-error -X PATCH \
  "$BASE/api/shippers/online-status?isOnline=false" \
  -H "Authorization: Bearer $shipper_token" >/dev/null
curl --fail-with-body --silent --show-error -X POST \
  "$BASE/api/tracking/shipper-locations/offline" \
  -H "Authorization: Bearer $shipper_token" >/dev/null

printf '[Task 21] PASS: normal/voucher/flash checkout, duplicate/exhausted/no-stack, '\
'cancel/replay, and Order-Delivery-Settlement reconciliation passed (order=%s delivery=%s).\n' \
  "$settlement_order_id" "$delivery_id"
