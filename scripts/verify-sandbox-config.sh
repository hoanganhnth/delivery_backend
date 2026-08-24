#!/usr/bin/env bash
# Static contract gate for the production-like sandbox overlay.
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly BACKEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$BACKEND_DIR"
unset COMPOSE_FILE COMPOSE_PROFILES

command -v docker >/dev/null || { echo "docker is required" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq is required" >&2; exit 1; }

export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-delivery_sandbox_contract}"
export SANDBOX_NETWORK_NAME="${SANDBOX_NETWORK_NAME:-delivery_sandbox_contract_network}"
export POSTGRES_VOLUME_NAME="${POSTGRES_VOLUME_NAME:-delivery_sandbox_contract_postgres}"
export KAFKA_VOLUME_NAME="${KAFKA_VOLUME_NAME:-delivery_sandbox_contract_kafka}"
export SANDBOX_SIMULATOR_API_TOKEN="${SANDBOX_SIMULATOR_API_TOKEN:-sandbox-contract-token}"
export SANDBOX_BATCH_ENABLED="${SANDBOX_BATCH_ENABLED:-true}"
export SANDBOX_BATCH_SCHEDULER_ENABLED="${SANDBOX_BATCH_SCHEDULER_ENABLED:-true}"
export SANDBOX_BATCH_CLIENT_CAPABILITY_ENABLED="${SANDBOX_BATCH_CLIENT_CAPABILITY_ENABLED:-true}"
export SANDBOX_H3_ENABLED="${SANDBOX_H3_ENABLED:-true}"
export INTERNAL_SECRET_FILE="${INTERNAL_SECRET_FILE:-.secrets/internal-secret}"
export DB_PASSWORD_FILE="${DB_PASSWORD_FILE:-.secrets/db-password}"
export JWT_PRIVATE_KEY_FILE="${JWT_PRIVATE_KEY_FILE:-.secrets/jwt-private.pem}"
export JWT_PUBLIC_KEY_FILE="${JWT_PUBLIC_KEY_FILE:-.secrets/jwt-public.pem}"
export GRAFANA_ADMIN_PASSWORD="${GRAFANA_ADMIN_PASSWORD:-sandbox-contract-grafana}"

config="$({
  docker compose \
    -f docker-compose.yml \
    -f docker-compose.secrets.yml \
    -f docker-compose.isolated-e2e.yml \
    -f docker-compose.simulator.yml \
    -f docker-compose.sandbox.yml \
    config --format json
})"

printf '%s' "$config" | jq -e \
  --arg expectedBatch "$SANDBOX_BATCH_ENABLED" \
  --arg expectedScheduler "$SANDBOX_BATCH_SCHEDULER_ENABLED" \
  --arg expectedCapability "$SANDBOX_BATCH_CLIENT_CAPABILITY_ENABLED" \
  --arg expectedH3 "$SANDBOX_H3_ENABLED" '
  . as $root
  | ($root.services["api-gateway"].ports | length) == 1
  and ($root.services["api-gateway"].ports[0].host_ip == "127.0.0.1")
  and ($root.services["api-gateway"].ports[0].target == 8079)
  and ($root.services["api-gateway"].ports[0].published == null)
  and ($root.services["simulator-service"].ports | length) == 1
  and ($root.services["simulator-service"].ports[0].host_ip == "127.0.0.1")
  and ($root.services["simulator-service"].ports[0].target == 8100)
  and ($root.services["simulator-service"].ports[0].published == null)
  and ($root.services.prometheus.ports[0].host_ip == "127.0.0.1")
  and ($root.services.prometheus.ports[0].target == 9090)
  and ($root.services.prometheus.ports[0].published == null)
  and ($root.services.grafana.ports[0].host_ip == "127.0.0.1")
  and ($root.services.grafana.ports[0].target == 3000)
  and ($root.services.grafana.ports[0].published == null)
  and ($root.services["simulator-service"].environment.SIMULATOR_ENABLED == "true")
  and ($root.services["simulator-service"].environment.SIMULATOR_ALLOW_NON_LOCAL_TARGETS == "false")
  and ($root.services["simulator-service"].environment.SIMULATOR_GATEWAY_BASE_URL == "http://api-gateway:8079")
  and ($root.services["simulator-service"].environment.SIMULATOR_ALLOWED_ORIGINS
       | contains("http://localhost:5173"))
  and ($root.services["simulator-service"].healthcheck.test[1]
       == "wget -q -T 3 -O /dev/null http://localhost:9100/actuator/health/readiness")
  and ($root.services.kafka.environment.KAFKA_HEAP_OPTS == "-Xms128m -Xmx384m")
  and ($root.services.elasticsearch.environment.ES_JAVA_OPTS == "-Xms96m -Xmx128m")
  and ($root.services.elasticsearch.environment["xpack.ml.enabled"] == "false")
  and ($root.services.elasticsearch.environment["xpack.monitoring.collection.enabled"] == "false")
  and ($root.services.elasticsearch.environment["xpack.watcher.enabled"] == "false")
  and ($root.services.elasticsearch.environment["ingest.geoip.downloader.enabled"] == "false")
  and ($root.services.elasticsearch.deploy.resources.limits.memory == "1610612736")
  and ($root.services["restaurant-service"].deploy.resources.limits.memory == "805306368")
  and ($root.services["notification-service"].deploy.resources.limits.memory == "805306368")
  and ($root.services["match-service"].deploy.resources.limits.memory == "805306368")
  and ([
    "api-gateway", "auth-service", "user-service", "restaurant-service",
    "order-service", "delivery-service", "search-service", "shipper-service",
    "settlement-service", "notification-service", "match-service", "tracking-service",
    "saga-orchestrator-service"
  ] | all(. as $service |
      $root.services[$service].environment.JAVA_TOOL_OPTIONS == "-Xmx160m -Xms48m"))
  and ($root.services["config-server"].environment.JAVA_TOOL_OPTIONS == "-Xmx192m -Xms64m")
  and ($root.services["discovery-server"].environment.JAVA_TOOL_OPTIONS == "-Xmx192m -Xms64m")
  and ($root.services["routing-service"].environment.JAVA_TOOL_OPTIONS == "-Xmx160m -Xms48m")
  and ($root.services["simulator-service"].environment.JAVA_TOOL_OPTIONS == "-Xmx192m -Xms64m")
  and ($root.services["match-service"].environment.MATCHING_H3_ENABLED == $expectedH3)
  and ($root.services["match-service"].environment.MATCHING_BATCH_ENABLED == $expectedBatch)
  and ($root.services["match-service"].environment.MATCHING_BATCH_SCHEDULER_ENABLED == $expectedScheduler)
  and ($root.services["delivery-service"].environment.DELIVERY_BATCH_ENABLED == $expectedBatch)
  and ($root.services["saga-orchestrator-service"].environment.MATCHING_BATCH_CLIENT_CAPABILITY_ENABLED == $expectedCapability)
  and ($root.networks["delivery-network"].name | startswith("delivery_sandbox_"))
  and ($root.volumes.postgres_data.name | startswith("delivery_sandbox_"))
  and ($root.volumes.kafka_data.name | startswith("delivery_sandbox_"))
  and ([
    "tracing-collector", "postgres", "redis", "kafka", "elasticsearch",
    "api-gateway", "auth-service", "user-service", "restaurant-service",
    "order-service", "delivery-service", "search-service", "shipper-service",
    "settlement-service", "notification-service", "match-service",
    "tracking-service", "routing-service", "saga-orchestrator-service",
    "prometheus", "grafana", "simulator-service"
  ] | all(. as $service | ($root.services[$service].container_name // null) == null))
  and ([
    "tracing-collector", "postgres", "redis", "kafka", "elasticsearch",
    "auth-service", "user-service", "restaurant-service", "order-service",
    "delivery-service", "search-service", "shipper-service", "settlement-service",
    "notification-service", "match-service", "tracking-service", "routing-service",
    "saga-orchestrator-service"
  ] | all(. as $service | (($root.services[$service].ports // []) | length) == 0))
' >/dev/null || {
  echo "Sandbox Compose safety/feature contract failed." >&2
  exit 1
}

echo "Sandbox Compose contract passed: isolated names, low-memory JVM/Kafka/Elasticsearch, loopback dynamic ports, mock-only simulator and H3/batch flags verified."
