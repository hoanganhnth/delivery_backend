#!/usr/bin/env bash
set -euo pipefail

# Runtime-only proof for the local monitoring topology. It checks the health of
# Prometheus and Grafana from inside their private network and verifies that the
# configured core targets are really being scraped, not merely listed in YAML.

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly PROJECT_NAME="${COMPOSE_PROJECT_NAME:-backend_delivery}"
readonly TARGET_TIMEOUT_SECONDS="${OBSERVABILITY_TARGET_TIMEOUT_SECONDS:-60}"
readonly -a EXPECTED_TARGETS=(
  api-gateway:9090
  order-service:9090
  restaurant-service:9090
  delivery-service:9090
  match-service:9090
  settlement-service:9090
  saga-orchestrator-service:9090
  notification-service:9090
)

cd "$ROOT_DIR"

command -v docker >/dev/null || { echo "docker is required." >&2; exit 1; }
command -v jq >/dev/null || { echo "jq is required." >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "Docker daemon is unavailable." >&2; exit 1; }

for container in delivery-prometheus delivery-grafana; do
  project="$(docker inspect "$container" --format '{{index .Config.Labels "com.docker.compose.project"}}' 2>/dev/null || true)"
  if [[ "$project" != "$PROJECT_NAME" ]]; then
    echo "${container} is not owned by Compose project ${PROJECT_NAME}." >&2
    exit 1
  fi
done

prometheus_ready="$(docker exec delivery-prometheus wget -qO- http://localhost:9090/-/ready)"
if [[ "$prometheus_ready" != *"Ready"* ]]; then
  echo "Prometheus readiness response was unexpected." >&2
  exit 1
fi

grafana_health="$(docker exec delivery-grafana wget -qO- http://localhost:3000/api/health)"
printf '%s' "$grafana_health" | jq -e '.database == "ok"' >/dev/null

expected_json="$(printf '%s\n' "${EXPECTED_TARGETS[@]}" | jq -R . | jq -s 'sort')"
deadline=$((SECONDS + TARGET_TIMEOUT_SECONDS))
targets_ready=false
while (( SECONDS < deadline )); do
  targets="$(docker exec delivery-prometheus wget -qO- http://localhost:9090/api/v1/targets)"
  if printf '%s' "$targets" | jq -e --argjson expected "$expected_json" '
    [ .data.activeTargets[]
      | select(.labels.job == "delivery-services")
      | select(.health == "up")
      | .labels.instance
    ] | sort == $expected
  ' >/dev/null; then
    targets_ready=true
    break
  fi
  sleep 5
done
if [[ "$targets_ready" != true ]]; then
  echo "Timed out waiting for every configured Prometheus target to report up." >&2
  exit 1
fi

# Grafana's unauthenticated health endpoint is enough for liveness, but use its
# container-local administrator credential to prove that the provisioned
# dashboard exists. The response is inspected only; no credential is printed.
dashboard="$(docker exec delivery-grafana sh -c \
  'curl -fsS -u "admin:${GF_SECURITY_ADMIN_PASSWORD}" http://localhost:3000/api/search')"
printf '%s' "$dashboard" | jq -e 'any(.[]; .uid == "delivery-operations")' >/dev/null

echo "PASS: Prometheus ready, Grafana healthy/dashboard provisioned, and all core scrape targets are up."
