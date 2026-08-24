#!/usr/bin/env bash
set -euo pipefail

# Recover the local Elasticsearch -> Search dependency without deleting or
# recreating any data volume. Disabled capability services are deliberately
# left stopped by default because they are not part of the MVP contract and
# can exhaust a small Docker Desktop VM when all JVMs run together.

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly PROJECT_NAME="${COMPOSE_PROJECT_NAME:-backend_delivery}"
readonly TIMEOUT_SECONDS="${LOCAL_SEARCH_RECOVERY_TIMEOUT_SECONDS:-180}"
readonly GATEWAY_SMOKE_TIMEOUT_SECONDS="${LOCAL_SEARCH_GATEWAY_SMOKE_TIMEOUT_SECONDS:-120}"
readonly GATEWAY_URL="${GATEWAY_URL:-http://127.0.0.1:8079}"
readonly SEARCH_QUERY="${SEARCH_QUERY:-pho}"
readonly -a COMPOSE_FILES=(
  -f docker-compose.yml
  -f docker-compose.secrets.yml
)
readonly -a DISABLED_SERVICES=(
  promotion-service
  flashsale-service
  analytics-service
  livestream-service
)

cd "$ROOT_DIR"

command -v docker >/dev/null || { echo "docker is required." >&2; exit 1; }
command -v curl >/dev/null || { echo "curl is required." >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "Docker daemon is unavailable." >&2; exit 1; }

compose() {
  COMPOSE_PROJECT_NAME="$PROJECT_NAME" \
  POSTGRES_HOST_PORT="$POSTGRES_HOST_PORT" \
  POSTGRES_VOLUME_NAME="$POSTGRES_VOLUME_NAME" \
  KAFKA_VOLUME_NAME="$KAFKA_VOLUME_NAME" \
    docker compose "${COMPOSE_FILES[@]}" "$@"
}

postgres_project="$(docker inspect delivery-postgres --format '{{index .Config.Labels "com.docker.compose.project"}}' 2>/dev/null || true)"
if [[ "$postgres_project" != "$PROJECT_NAME" ]]; then
  echo "Refusing recovery: delivery-postgres is not owned by Compose project ${PROJECT_NAME}." >&2
  exit 1
fi

# The canonical stack can have an operator-selected host port/volume. Reusing
# those values is mandatory: using Compose defaults may make it reconcile and
# recreate PostgreSQL even though Search recovery never needs to touch it.
detected_postgres_volume="$(docker inspect delivery-postgres --format \
  '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}')"
detected_kafka_volume="$(docker inspect delivery-kafka --format \
  '{{range .Mounts}}{{if eq .Destination "/var/lib/kafka/data"}}{{.Name}}{{end}}{{end}}')"
detected_postgres_host_port="$(docker inspect delivery-postgres --format \
  '{{(index (index .NetworkSettings.Ports "5432/tcp") 0).HostPort}}' 2>/dev/null || true)"

if [[ -z "$detected_postgres_volume" || -z "$detected_kafka_volume" || -z "$detected_postgres_host_port" ]]; then
  echo "Refusing recovery: could not discover the canonical PostgreSQL/Kafka volume or PostgreSQL host port." >&2
  exit 1
fi
if [[ -n "${POSTGRES_HOST_PORT:-}" && "$POSTGRES_HOST_PORT" != "$detected_postgres_host_port" ]]; then
  echo "Refusing recovery: POSTGRES_HOST_PORT differs from the mounted canonical stack." >&2
  exit 1
fi
if [[ -n "${POSTGRES_VOLUME_NAME:-}" && "$POSTGRES_VOLUME_NAME" != "$detected_postgres_volume" ]]; then
  echo "Refusing recovery: POSTGRES_VOLUME_NAME differs from the mounted canonical stack." >&2
  exit 1
fi
if [[ -n "${KAFKA_VOLUME_NAME:-}" && "$KAFKA_VOLUME_NAME" != "$detected_kafka_volume" ]]; then
  echo "Refusing recovery: KAFKA_VOLUME_NAME differs from the mounted canonical stack." >&2
  exit 1
fi

export POSTGRES_HOST_PORT="$detected_postgres_host_port"
export POSTGRES_VOLUME_NAME="$detected_postgres_volume"
export KAFKA_VOLUME_NAME="$detected_kafka_volume"

for service in elasticsearch search-service; do
  if [[ -z "$(compose ps -aq "$service")" ]]; then
    echo "Refusing recovery: ${service} container does not exist; use the normal startup runbook instead." >&2
    exit 1
  fi
done

# Render before changing container state. This also catches missing operator
# secret-file variables without printing their values.
compose config --quiet

wait_for_health() {
  local service="$1"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  local container_id state health

  while (( SECONDS < deadline )); do
    container_id="$(compose ps -q "$service")"
    if [[ -n "$container_id" ]]; then
      state="$(docker inspect --format '{{.State.Status}}' "$container_id")"
      health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container_id")"
      if [[ "$health" == "healthy" ]]; then
        return 0
      fi
      if [[ "$state" == "exited" || "$state" == "dead" ]]; then
        compose logs --no-color --tail=120 "$service" >&2 || true
        echo "${service} exited before becoming healthy." >&2
        return 1
      fi
    fi
    sleep 5
  done

  compose logs --no-color --tail=120 "$service" >&2 || true
  echo "Timed out waiting for ${service} health." >&2
  return 1
}

echo "Local search recovery: stopping disabled capability services and Search only."
compose stop --timeout 30 "${DISABLED_SERVICES[@]}" search-service >/dev/null 2>&1 || true

echo "Local search recovery: starting the existing Elasticsearch container without rebuilding or recreating it."
compose start elasticsearch >/dev/null
wait_for_health elasticsearch

echo "Local search recovery: starting the existing Search container without rebuilding or recreating it."
compose start search-service >/dev/null
wait_for_health search-service

gateway_deadline=$((SECONDS + GATEWAY_SMOKE_TIMEOUT_SECONDS))
http_code="000"
while (( SECONDS < gateway_deadline )); do
  http_code="$(curl --silent --max-time 15 -o /dev/null -w '%{http_code}' \
    "${GATEWAY_URL}/api/search/restaurants?q=${SEARCH_QUERY}&page=0&size=1" 2>/dev/null || true)"
  if [[ "$http_code" == "200" ]]; then
    break
  fi
  sleep 2
done
if [[ "$http_code" != "200" ]]; then
  echo "Search Gateway smoke returned HTTP ${http_code}; inspect Search logs before retrying." >&2
  exit 1
fi

echo "PASS: Elasticsearch and Search are healthy; Gateway search smoke returned HTTP 200."
echo "Disabled capability services remain stopped; start them manually only after checking available memory."
compose ps elasticsearch search-service
