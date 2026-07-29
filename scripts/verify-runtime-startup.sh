#!/usr/bin/env bash
set -euo pipefail

readonly STARTUP_TIMEOUT_SECONDS="${STARTUP_TIMEOUT_SECONDS:-420}"
readonly POLL_SECONDS=5
readonly -a COMPOSE_COMMAND=(
  docker compose
  -f docker-compose.yml
  -f docker-compose.secrets.yml
)

readonly INFRA_SERVICES=(postgres redis kafka elasticsearch)
readonly APP_SERVICES=(
  api-gateway
  auth-service
  user-service
  restaurant-service
  order-service
  delivery-service
  search-service
  shipper-service
  settlement-service
  notification-service
  match-service
  tracking-service
  livestream-service
  saga-orchestrator-service
  promotion-service
  analytics-service
  flashsale-service
)

command -v docker >/dev/null
command -v curl >/dev/null
command -v openssl >/dev/null
command -v cmp >/dev/null

# Docker Compose tự đọc .env, nhưng shell script cần cùng giá trị cho preflight.
# Chỉ parse đúng hai key do gen-keys.sh tạo; không source/execute nội dung file.
if [[ -f .env ]]; then
  INTERNAL_SECRET="${INTERNAL_SECRET:-$(sed -n 's/^INTERNAL_SECRET=//p' .env | tail -n 1)}"
  POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-$(sed -n 's/^POSTGRES_PASSWORD=//p' .env | tail -n 1)}"
  export INTERNAL_SECRET POSTGRES_PASSWORD
fi

if ! docker info >/dev/null 2>&1; then
  printf '%s\n' "Docker daemon is unavailable; runtime startup proof was not executed." >&2
  exit 1
fi

# Preserve the exact canonical data volumes and host port that are mounted now.
# The canonical stack may have been promoted from an isolated B8 rehearsal and
# therefore legitimately differ from Compose's generated defaults. Recreating
# it with guessed defaults can boot an older/empty database or collide with a
# host PostgreSQL process.
existing_postgres_project="$(docker inspect delivery-postgres \
  --format '{{index .Config.Labels "com.docker.compose.project"}}' 2>/dev/null || true)"
if [[ -n "$existing_postgres_project" && "$existing_postgres_project" != "backend_delivery" ]]; then
  printf 'Container delivery-postgres belongs to unexpected project %s; refusing startup reconcile.\n' \
    "$existing_postgres_project" >&2
  exit 1
fi

if [[ -n "$existing_postgres_project" ]]; then
  existing_postgres_running="$(docker inspect delivery-postgres \
    --format '{{.State.Running}}')"
  detected_postgres_volume="$(docker inspect delivery-postgres --format \
    '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}')"
  detected_postgres_host_port=""
  if [[ "$existing_postgres_running" == "true" ]]; then
    detected_postgres_host_port="$(docker inspect delivery-postgres --format \
      '{{(index (index .NetworkSettings.Ports "5432/tcp") 0).HostPort}}')"
  fi
  detected_kafka_volume="$(docker inspect delivery-kafka --format \
    '{{range .Mounts}}{{if eq .Destination "/var/lib/kafka/data"}}{{.Name}}{{end}}{{end}}')"

  if [[ -n "${POSTGRES_VOLUME_NAME:-}" && "$POSTGRES_VOLUME_NAME" != "$detected_postgres_volume" ]]; then
    printf 'Configured PostgreSQL volume %s does not match mounted volume %s.\n' \
      "$POSTGRES_VOLUME_NAME" "$detected_postgres_volume" >&2
    exit 1
  fi
  if [[ -n "${POSTGRES_HOST_PORT:-}" \
      && -n "$detected_postgres_host_port" \
      && "$POSTGRES_HOST_PORT" != "$detected_postgres_host_port" ]]; then
    printf 'Configured PostgreSQL host port %s does not match published port %s.\n' \
      "$POSTGRES_HOST_PORT" "$detected_postgres_host_port" >&2
    exit 1
  fi
  if [[ -n "${KAFKA_VOLUME_NAME:-}" && "$KAFKA_VOLUME_NAME" != "$detected_kafka_volume" ]]; then
    printf 'Configured Kafka volume %s does not match mounted volume %s.\n' \
      "$KAFKA_VOLUME_NAME" "$detected_kafka_volume" >&2
    exit 1
  fi

  export POSTGRES_VOLUME_NAME="${POSTGRES_VOLUME_NAME:-$detected_postgres_volume}"
  export POSTGRES_HOST_PORT="${POSTGRES_HOST_PORT:-$detected_postgres_host_port}"
  export KAFKA_VOLUME_NAME="${KAFKA_VOLUME_NAME:-$detected_kafka_volume}"
fi

if [[ -z "${INTERNAL_SECRET:-}" ]]; then
  printf '%s\n' "INTERNAL_SECRET must be non-blank for cross-service runtime proof." >&2
  exit 1
fi
if [[ -z "${POSTGRES_PASSWORD:-}" ]]; then
  printf '%s\n' "POSTGRES_PASSWORD must be non-blank for runtime proof." >&2
  exit 1
fi

openssl pkey -in auth-service/src/main/resources/private.pem -pubout -outform PEM 2>/dev/null \
  | cmp -s - auth-service/src/main/resources/public.pem
cmp -s \
  auth-service/src/main/resources/public.pem \
  api-gateway/src/main/resources/public.pem

bash scripts/verify-compose-config.sh
"${COMPOSE_COMMAND[@]}" config --quiet
"${COMPOSE_COMMAND[@]}" up -d --build

deadline=$((SECONDS + STARTUP_TIMEOUT_SECONDS))

wait_for_infra() {
  local service="$1"
  local container_id health
  while (( SECONDS < deadline )); do
    container_id="$("${COMPOSE_COMMAND[@]}" ps -aq "$service")"
    if [[ -n "$container_id" ]]; then
      health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id")"
      if [[ "$health" == "healthy" ]]; then
        return 0
      fi
      if [[ "$health" == "exited" || "$health" == "dead" || "$health" == "unhealthy" ]]; then
        "${COMPOSE_COMMAND[@]}" logs --no-color --tail=120 "$service" >&2
        return 1
      fi
    fi
    sleep "$POLL_SECONDS"
  done
  "${COMPOSE_COMMAND[@]}" logs --no-color --tail=120 "$service" >&2
  return 1
}

wait_for_app() {
  local service="$1"
  local container_id state
  while (( SECONDS < deadline )); do
    container_id="$("${COMPOSE_COMMAND[@]}" ps -aq "$service")"
    if [[ -n "$container_id" ]]; then
      state="$(docker inspect --format '{{.State.Status}}' "$container_id")"
      if [[ "$state" == "running" ]] \
          && "${COMPOSE_COMMAND[@]}" logs --no-color "$service" 2>&1 \
            | grep -E 'Started .*Application' >/dev/null; then
        return 0
      fi
      if [[ "$state" == "exited" || "$state" == "dead" ]]; then
        "${COMPOSE_COMMAND[@]}" logs --no-color --tail=160 "$service" >&2
        return 1
      fi
    fi
    sleep "$POLL_SECONDS"
  done
  "${COMPOSE_COMMAND[@]}" logs --no-color --tail=160 "$service" >&2
  return 1
}

for service in "${INFRA_SERVICES[@]}"; do
  wait_for_infra "$service"
done

for service in "${APP_SERVICES[@]}"; do
  wait_for_app "$service"
done

curl --fail --silent --show-error --max-time 15 \
  "http://127.0.0.1:8079/api/restaurants" >/dev/null
curl --fail --silent --show-error --max-time 15 \
  "http://127.0.0.1:8079/api/search/restaurants?q=pho&page=0&size=1" >/dev/null

printf '%s\n' \
  "Runtime startup proof passed: canonical volumes preserved, infrastructure healthy, 17 applications started, Gateway public reads responded."
