#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Every application module gets the same test-only bootstrap boundary. The
# runtime configuration remains in src/main/resources and is intentionally not
# checked by this verifier.
modules=(
  api-gateway auth-service user-service restaurant-service order-service
  delivery-service search-service shipper-service settlement-service
  notification-service match-service tracking-service livestream-service
  saga-orchestrator-service promotion-service analytics-service flashsale-service
)

required_patterns=(
  '^spring\.config\.import=optional:configserver:$'
  '^spring\.cloud\.config\.enabled=false$'
  '^spring\.cloud\.config\.import-check\.enabled=false$'
  '^spring\.cloud\.discovery\.enabled=false$'
  '^eureka\.client\.enabled=false$'
  '^eureka\.client\.register-with-eureka=false$'
  '^eureka\.client\.fetch-registry=false$'
  '^spring\.kafka\.listener\.auto-startup=false$'
  '^spring\.kafka\.admin\.auto-create=false$'
  '^spring\.task\.scheduling\.enabled=false$'
)

for module in "${modules[@]}"; do
  config="${ROOT_DIR}/${module}/src/test/resources/application.properties"
  if [[ ! -f "${config}" ]]; then
    echo "${module}: missing test application.properties." >&2
    exit 1
  fi

  for pattern in "${required_patterns[@]}"; do
    if ! rg -q "${pattern}" "${config}"; then
      echo "${module}: missing test isolation property matching ${pattern}." >&2
      exit 1
    fi
  done

  if rg -n 'configserver:http|discovery-server:8761|localhost:9092' "${config}" >/dev/null; then
    echo "${module}: test bootstrap contains an external infrastructure endpoint." >&2
    exit 1
  fi
done

echo "All ${#modules[@]} backend application test contexts are isolated from external config, discovery, Kafka and schedulers."
