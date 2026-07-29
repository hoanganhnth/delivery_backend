#!/usr/bin/env bash
set -euo pipefail

readonly FLOW_TIMEOUT_SECONDS="${FLOW_TIMEOUT_SECONDS:-60}"
readonly POLL_SECONDS="${POLL_SECONDS:-2}"
readonly SHIPPER_ID="${SHIPPER_ID:-$((930000000 + ($(date +%s) % 1000000)))}"
readonly RAW_MEMBER="${SHIPPER_ID}"
readonly JSON_MEMBER="\"${SHIPPER_ID}\""
readonly GEO_KEY="match:shippers:geo"
readonly ONLINE_SET_KEY="match:shippers:online"
readonly FRESHNESS_KEY="match:shipper:location-fresh:${SHIPPER_ID}"

command -v docker >/dev/null

step() {
  printf '[MATCH-LOCATION] %s\n' "$1"
}

redis_cli() {
  docker compose exec -T redis redis-cli "$@"
}

produce_location_event() {
  local timestamp="$1" online="$2" latitude="$3" longitude="$4"
  local payload
  if [[ "$online" == "true" ]]; then
    payload="{\"shipperId\":${SHIPPER_ID},\"latitude\":${latitude},\"longitude\":${longitude},\"isOnline\":true,\"timestamp\":${timestamp}}"
  else
    payload="{\"shipperId\":${SHIPPER_ID},\"latitude\":null,\"longitude\":null,\"isOnline\":false,\"timestamp\":${timestamp}}"
  fi

  printf '%s:%s\n' "$SHIPPER_ID" "$payload" | docker compose exec -T kafka \
    kafka-console-producer --bootstrap-server kafka:9092 \
    --topic shipper.location-updated \
    --property parse.key=true \
    --property key.separator=: >/dev/null
}

cleanup_fixture() {
  # MatchRedisGeoRepository uses GenericJackson2JsonRedisSerializer for values,
  # so GEO/set members are stored as JSON strings in production Redis. Remove the
  # raw form too so old/manual fixtures cannot pollute this proof.
  redis_cli SREM "$ONLINE_SET_KEY" "$RAW_MEMBER" "$JSON_MEMBER" >/dev/null || true
  redis_cli ZREM "$GEO_KEY" "$RAW_MEMBER" "$JSON_MEMBER" >/dev/null || true
  redis_cli DEL "$FRESHNESS_KEY" \
    "match:shipper:busy:${SHIPPER_ID}" \
    "match:shipper:offer:${SHIPPER_ID}" \
    "match:shipper:status-version:${SHIPPER_ID}" >/dev/null || true
}

require_running_service() {
  local service="$1" container_id running
  container_id="$(docker compose ps -q "$service")"
  if [[ -z "$container_id" ]]; then
    printf 'Compose service %s is not created; start canonical Compose before running this proof.\n' \
      "$service" >&2
    exit 1
  fi
  running="$(docker inspect --format '{{.State.Running}}' "$container_id")"
  if [[ "$running" != "true" ]]; then
    printf 'Compose service %s is not running.\n' "$service" >&2
    exit 1
  fi
}

is_online() {
  [[ "$(redis_cli SISMEMBER "$ONLINE_SET_KEY" "$JSON_MEMBER" | tr -d '\r')" == "1" ]]
}

has_geo() {
  [[ -n "$(redis_cli --raw GEOPOS "$GEO_KEY" "$JSON_MEMBER" | tr -d '\r\n')" ]]
}

has_freshness() {
  [[ "$(redis_cli EXISTS "$FRESHNESS_KEY" | tr -d '\r')" == "1" ]]
}

wait_until() {
  local description="$1" predicate="$2"
  local deadline=$((SECONDS + FLOW_TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    if "$predicate"; then
      return 0
    fi
    sleep "$POLL_SECONDS"
  done
  printf 'Timed out waiting for %s for shipper %s.\n' "$description" "$SHIPPER_ID" >&2
  docker compose logs --no-color --tail=120 match-service >&2 || true
  return 1
}

online_projection_present() {
  is_online && has_geo && has_freshness
}

offline_tombstone_present() {
  ! is_online && ! has_geo && has_freshness
}

if ! docker info >/dev/null 2>&1; then
  printf '%s\n' "Docker daemon is unavailable; Match location replay proof was not executed." >&2
  exit 1
fi

require_running_service redis
require_running_service kafka
require_running_service match-service

trap cleanup_fixture EXIT
cleanup_fixture

base_timestamp_ms=$(( $(date +%s) * 1000 ))
online_timestamp=$base_timestamp_ms
offline_timestamp=$((base_timestamp_ms + 1000))
older_replay_timestamp=$((base_timestamp_ms + 500))
newer_online_timestamp=$((base_timestamp_ms + 2000))

step "produce online location and wait for Match Redis projection"
produce_location_event "$online_timestamp" true 10.7700 106.7000
wait_until "online GEO + set + freshness projection" online_projection_present

step "produce newer offline tombstone and wait for GEO/online removal"
produce_location_event "$offline_timestamp" false null null
wait_until "offline tombstone projection" offline_tombstone_present

step "replay older online event and verify it cannot resurrect the shipper"
produce_location_event "$older_replay_timestamp" true 10.7700 106.7000
sleep "$POLL_SECONDS"
if ! offline_tombstone_present; then
  printf 'Older online replay resurrected shipper %s after offline tombstone.\n' \
    "$SHIPPER_ID" >&2
  exit 1
fi

step "produce newer online event and wait for eligibility projection to return"
produce_location_event "$newer_online_timestamp" true 10.7710 106.7010
wait_until "newer online projection" online_projection_present

printf 'Match location replay proof passed: shipper=%s, offline tombstone fenced older online replay and newer online update restored projection.\n' \
  "$SHIPPER_ID"
