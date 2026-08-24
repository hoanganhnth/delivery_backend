#!/usr/bin/env bash
set -euo pipefail

readonly STARTUP_TIMEOUT_SECONDS="${STARTUP_TIMEOUT_SECONDS:-420}"
readonly POLL_SECONDS=5
readonly EUREKA_REGISTRATION_TIMEOUT_SECONDS="${EUREKA_REGISTRATION_TIMEOUT_SECONDS:-120}"
readonly RUNTIME_ISOLATED="${RUNTIME_ISOLATED:-false}"
readonly RUNTIME_RESOURCE_START_MODE="${RUNTIME_RESOURCE_START_MODE:-parallel}"
readonly RUNTIME_REBUILD_IMAGES="${RUNTIME_REBUILD_IMAGES:-false}"
readonly RUNTIME_EXTRA_COMPOSE_FILES="${RUNTIME_EXTRA_COMPOSE_FILES:-}"
readonly RUNTIME_INCLUDE_SIMULATOR="${RUNTIME_INCLUDE_SIMULATOR:-false}"
case "$RUNTIME_ISOLATED" in
  true|false)
    ;;
  *)
    printf 'RUNTIME_ISOLATED must be true or false, got %s\n' "$RUNTIME_ISOLATED" >&2
    exit 1
    ;;
esac
if [[ ! "$EUREKA_REGISTRATION_TIMEOUT_SECONDS" =~ ^[0-9]+$ || "$EUREKA_REGISTRATION_TIMEOUT_SECONDS" -le 0 ]]; then
  printf 'EUREKA_REGISTRATION_TIMEOUT_SECONDS must be a positive integer, got %s\n' \
    "$EUREKA_REGISTRATION_TIMEOUT_SECONDS" >&2
  exit 1
fi
case "$RUNTIME_REBUILD_IMAGES" in
  true|false)
    ;;
  *)
    printf 'RUNTIME_REBUILD_IMAGES must be true or false, got %s\n' \
      "$RUNTIME_REBUILD_IMAGES" >&2
    exit 1
    ;;
esac
case "$RUNTIME_INCLUDE_SIMULATOR" in
  true|false)
    ;;
  *)
    printf 'RUNTIME_INCLUDE_SIMULATOR must be true or false, got %s\n' \
      "$RUNTIME_INCLUDE_SIMULATOR" >&2
    exit 1
    ;;
esac

COMPOSE_COMMAND=(
  docker compose
  -f docker-compose.yml
  -f docker-compose.secrets.yml
)
if [[ "$RUNTIME_ISOLATED" == "true" ]]; then
  if [[ -z "${COMPOSE_PROJECT_NAME:-}" || "${COMPOSE_PROJECT_NAME}" == "backend_delivery" ]]; then
    printf '%s\n' \
      'RUNTIME_ISOLATED=true requires a non-canonical COMPOSE_PROJECT_NAME.' >&2
    exit 1
  fi
  COMPOSE_COMMAND+=(-f docker-compose.isolated-e2e.yml)
fi
# Optional overlays are deliberately explicit so the canonical verifier keeps
# its historical file set. The sandbox passes simulator + sandbox overlays as
# a colon-separated list; no value is sourced or executed.
if [[ -n "$RUNTIME_EXTRA_COMPOSE_FILES" ]]; then
  IFS=':' read -r -a extra_compose_files <<< "$RUNTIME_EXTRA_COMPOSE_FILES"
  for compose_file in "${extra_compose_files[@]}"; do
    [[ -n "$compose_file" && -f "$compose_file" ]] || {
      printf 'Runtime extra Compose file does not exist: %s\n' "$compose_file" >&2
      exit 1
    }
    COMPOSE_COMMAND+=(-f "$compose_file")
  done
fi
readonly -a COMPOSE_COMMAND

# The canonical runtime verifier defaults to reconciling existing images. An
# unconditional Compose --build generates fresh image IDs and recreates every
# service even when source/configuration did not change. Opt into that release
# behavior with RUNTIME_REBUILD_IMAGES=true; the disposable E2E runner always
# does so because it must consume the just-packaged artifacts.
compose_up() {
  if [[ "$RUNTIME_REBUILD_IMAGES" == "true" ]]; then
    "${COMPOSE_COMMAND[@]}" up -d --build "$@"
  else
    "${COMPOSE_COMMAND[@]}" up -d "$@"
  fi
}

readonly ALL_INFRA_SERVICES=(postgres redis kafka elasticsearch)
readonly ALL_OBSERVABILITY_SERVICES=(prometheus grafana)
readonly CONTROL_PLANE_SERVICES=(config-server discovery-server)
readonly CORE_APP_SERVICES=(
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
  routing-service
  saga-orchestrator-service
)
readonly OPTIONAL_CAPABILITY_SERVICES=(
  livestream-service
  promotion-service
  analytics-service
  flashsale-service
)
readonly ALL_APP_SERVICES=("${CORE_APP_SERVICES[@]}" "${OPTIONAL_CAPABILITY_SERVICES[@]}")
readonly INCLUDE_DISABLED_CAPABILITIES="${RUNTIME_INCLUDE_DISABLED_CAPABILITIES:-false}"
readonly INCLUDE_OBSERVABILITY="${RUNTIME_INCLUDE_OBSERVABILITY:-true}"

read -r -a INFRA_SERVICES <<< "${RUNTIME_INFRA_SERVICES:-${ALL_INFRA_SERVICES[*]}}"
read -r -a OBSERVABILITY_SERVICES <<< "${RUNTIME_OBSERVABILITY_SERVICES:-${ALL_OBSERVABILITY_SERVICES[*]}}"

contains_service() {
  local wanted="$1"
  shift
  local service
  for service in "$@"; do
    [[ "$service" == "$wanted" ]] && return 0
  done
  return 1
}

for service in "${INFRA_SERVICES[@]}"; do
  contains_service "$service" "${ALL_INFRA_SERVICES[@]}" || {
    printf 'Unsupported RUNTIME_INFRA_SERVICES entry: %s\n' "$service" >&2
    exit 1
  }
done
for service in "${OBSERVABILITY_SERVICES[@]}"; do
  contains_service "$service" "${ALL_OBSERVABILITY_SERVICES[@]}" || {
    printf 'Unsupported RUNTIME_OBSERVABILITY_SERVICES entry: %s\n' "$service" >&2
    exit 1
  }
done
case "$INCLUDE_OBSERVABILITY" in
  true|false) ;;
  *)
    printf 'RUNTIME_INCLUDE_OBSERVABILITY must be true or false, got %s\n' \
      "$INCLUDE_OBSERVABILITY" >&2
    exit 1
    ;;
esac

if [[ -n "${RUNTIME_APP_SERVICES:-}" ]]; then
  read -r -a APP_SERVICES <<< "$RUNTIME_APP_SERVICES"
else
  APP_SERVICES=("${CORE_APP_SERVICES[@]}")
fi
for service in "${APP_SERVICES[@]}"; do
  contains_service "$service" "${ALL_APP_SERVICES[@]}" || {
    printf 'Unsupported RUNTIME_APP_SERVICES entry: %s\n' "$service" >&2
    exit 1
  }
done
case "$INCLUDE_DISABLED_CAPABILITIES" in
  true)
    APP_SERVICES+=("${OPTIONAL_CAPABILITY_SERVICES[@]}")
    export COMPOSE_PROFILES="${COMPOSE_PROFILES:+${COMPOSE_PROFILES},}optional-capabilities"
    ;;
  false)
    ;;
  *)
    printf 'RUNTIME_INCLUDE_DISABLED_CAPABILITIES must be true or false, got %s\n' \
      "$INCLUDE_DISABLED_CAPABILITIES" >&2
    exit 1
    ;;
esac

RESOURCE_APP_SERVICES=()
for service in "${APP_SERVICES[@]}"; do
  if [[ "$service" != "auth-service" && "$service" != "api-gateway" ]]; then
    RESOURCE_APP_SERVICES+=("$service")
  fi
done

case "$RUNTIME_RESOURCE_START_MODE" in
  parallel|sequential)
    ;;
  *)
    printf 'RUNTIME_RESOURCE_START_MODE must be parallel or sequential, got %s\n' \
      "$RUNTIME_RESOURCE_START_MODE" >&2
    exit 1
    ;;
esac

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
# host PostgreSQL process. An isolated run must intentionally skip this lookup:
# it has project-scoped containers and fresh volumes and must never inspect or
# reconcile the developer's canonical stack.
if [[ "$RUNTIME_ISOLATED" == "false" ]]; then
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
  local container_id state health
  while (( SECONDS < deadline )); do
    container_id="$("${COMPOSE_COMMAND[@]}" ps -aq "$service")"
    if [[ -n "$container_id" ]]; then
      state="$(docker inspect --format '{{.State.Status}}' "$container_id")"
      health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container_id")"
      if [[ "$state" == "running" && "$health" == "healthy" ]]; then
        return 0
      fi
      if [[ "$state" == "exited" || "$state" == "dead" || "$health" == "unhealthy" ]]; then
        "${COMPOSE_COMMAND[@]}" logs --no-color --tail=160 "$service" >&2
        return 1
      fi
    fi
    sleep "$POLL_SECONDS"
  done
  "${COMPOSE_COMMAND[@]}" logs --no-color --tail=160 "$service" >&2
  return 1
}

# A healthy JVM is not enough to receive Gateway traffic. Eureka can lose its
# in-memory registry when the control plane is recreated while unchanged
# application containers keep running. Wait for the service's actual UP lease,
# and recreate only that service once if it never re-registers. This preserves
# local volumes and avoids the previous failure mode where Gateway started with
# an empty registry and returned 503 for otherwise healthy services.
eureka_registration_is_up() {
  local service="$1"
  local app_name
  local response
  app_name="$(printf '%s' "$service" | tr '[:lower:]' '[:upper:]')"
  response="$("${COMPOSE_COMMAND[@]}" exec -T discovery-server wget -q -T 3 -O - \
    "http://localhost:8761/eureka/apps/${app_name}" 2>/dev/null || true)"
  [[ "$response" == *"<name>${app_name}</name>"* \
      && "$response" == *"<status>UP</status>"* ]]
}

wait_for_eureka_registration() {
  local service="$1"
  local registration_deadline=$((SECONDS + EUREKA_REGISTRATION_TIMEOUT_SECONDS))
  while (( SECONDS < registration_deadline )); do
    if eureka_registration_is_up "$service"; then
      return 0
    fi
    sleep "$POLL_SECONDS"
  done
  return 1
}

ensure_eureka_registration() {
  local service="$1"
  if wait_for_eureka_registration "$service"; then
    return 0
  fi

  printf 'Eureka registration missing for %s; recreating only that service to refresh its lease.\n' \
    "$service" >&2
  "${COMPOSE_COMMAND[@]}" up -d --no-deps --force-recreate "$service"
  wait_for_app "$service" || return 1
  if ! wait_for_eureka_registration "$service"; then
    "${COMPOSE_COMMAND[@]}" logs --no-color --tail=160 "$service" >&2 || true
    printf 'Eureka registration did not converge for %s after targeted restart.\n' \
      "$service" >&2
    return 1
  fi
}

wait_for_gateway_http() {
  local path="$1"
  local deadline=$((SECONDS + STARTUP_TIMEOUT_SECONDS))
  local status published_port gateway_port base
  while (( SECONDS < deadline )); do
    base="${GATEWAY_BASE:-}"
    if [[ -z "$base" ]]; then
      published_port="$("${COMPOSE_COMMAND[@]}" port api-gateway 8079 2>/dev/null | head -n 1 || true)"
      gateway_port="${published_port##*:}"
      if [[ "$gateway_port" =~ ^[0-9]+$ ]]; then
        base="http://127.0.0.1:${gateway_port}"
      fi
    fi
    status="$(curl --silent --max-time 15 -o /dev/null -w '%{http_code}' \
      "${base}${path}" 2>/dev/null || true)"
    if [[ "$status" == "200" ]]; then
      return 0
    fi
    sleep "$POLL_SECONDS"
  done
  "${COMPOSE_COMMAND[@]}" logs --no-color --tail=160 api-gateway >&2 || true
  printf 'Gateway public smoke did not converge for %s.\n' "$path" >&2
  return 1
}

echo "Starting control plane before config-fail-fast application workloads."
compose_up tracing-collector "${CONTROL_PLANE_SERVICES[@]}"
for service in "${CONTROL_PLANE_SERVICES[@]}"; do
  wait_for_infra "$service"
done

echo "Starting local data plane."
compose_up "${INFRA_SERVICES[@]}"
for service in "${INFRA_SERVICES[@]}"; do
  wait_for_infra "$service"
done

echo "Starting monitoring dependencies."
if [[ "$INCLUDE_OBSERVABILITY" == "true" ]]; then
  compose_up "${OBSERVABILITY_SERVICES[@]}"
  for service in "${OBSERVABILITY_SERVICES[@]}"; do
    wait_for_infra "$service"
  done
else
  echo "RUNTIME_INCLUDE_OBSERVABILITY=false — skipping Prometheus/Grafana."
fi

echo "Starting Auth before JWKS resource services."
# Control plane and the data plane have already passed their explicit health
# gates. Do not let Compose traverse depends_on here and recreate Config/Eureka
# while the application wave is starting.
compose_up --no-deps auth-service
wait_for_app auth-service
ensure_eureka_registration auth-service

echo "Starting ${#RESOURCE_APP_SERVICES[@]} resource services ($RUNTIME_RESOURCE_START_MODE)."
if [[ "$RUNTIME_RESOURCE_START_MODE" == "parallel" ]]; then
  compose_up --no-deps "${RESOURCE_APP_SERVICES[@]}"
fi
for service in "${RESOURCE_APP_SERVICES[@]}"; do
  if [[ "$RUNTIME_RESOURCE_START_MODE" == "sequential" ]]; then
    compose_up --no-deps "$service"
  fi
  wait_for_app "$service"
  ensure_eureka_registration "$service"
done

echo "Starting Gateway after Auth and resource services."
compose_up --no-deps api-gateway
wait_for_app api-gateway
ensure_eureka_registration api-gateway

wait_for_gateway_http "/api/restaurants"
if contains_service search-service "${APP_SERVICES[@]}"; then
  wait_for_gateway_http "/api/search/restaurants?q=pho&page=0&size=1"
fi

if [[ "$RUNTIME_INCLUDE_SIMULATOR" == "true" ]]; then
  echo "Starting dev/test-only simulator after Gateway public smoke."
  compose_up --no-deps simulator-service
  wait_for_app simulator-service
fi

runtime_scope="canonical volumes preserved"
if [[ "$RUNTIME_ISOLATED" == "true" ]]; then
  runtime_scope="isolated project/volumes"
fi
printf '%s\n' \
  "Runtime startup proof passed: ${runtime_scope}, infrastructure healthy, ${#APP_SERVICES[@]} application services started$([[ "$INCLUDE_OBSERVABILITY" == "true" ]] && printf ', observability healthy' || true)$([[ "$RUNTIME_INCLUDE_SIMULATOR" == "true" ]] && printf ', simulator ready' || true), Gateway public reads responded."
