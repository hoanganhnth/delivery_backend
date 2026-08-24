#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
modules=(
  api-gateway auth-service user-service restaurant-service order-service
  delivery-service search-service shipper-service settlement-service
  notification-service match-service tracking-service livestream-service
  saga-orchestrator-service promotion-service analytics-service flashsale-service
)

for module in "${modules[@]}"; do
  pom="${ROOT_DIR}/${module}/pom.xml"
  config="${ROOT_DIR}/${module}/src/main/resources/application.properties"
  [[ -f "${config}" ]] || config="${ROOT_DIR}/${module}/src/main/resources/application.yml"

  [[ "$(rg -c '<artifactId>spring-boot-starter-actuator</artifactId>' "${pom}")" == "1" ]] \
    || { echo "${module}: expected exactly one Actuator dependency." >&2; exit 1; }
  rg -q 'management\.server\.port=\$\{MANAGEMENT_SERVER_PORT:9090\}' "${config}" \
    || rg -q 'port: \$\{MANAGEMENT_SERVER_PORT:9090\}' "${config}" \
    || { echo "${module}: management must use private port 9090 by default." >&2; exit 1; }
  rg -q 'management\.endpoint\.health\.probes\.enabled=true|probes:[[:space:]]*$' "${config}" \
    || { echo "${module}: liveness/readiness probes are not enabled." >&2; exit 1; }
  rg -q 'management\.health\.livenessstate\.enabled=true|livenessstate:[[:space:]]*$' "${config}" \
    || { echo "${module}: liveness state health contributor is disabled." >&2; exit 1; }
  rg -q 'management\.health\.readinessstate\.enabled=true|readinessstate:[[:space:]]*$' "${config}" \
    || { echo "${module}: readiness state health contributor is disabled." >&2; exit 1; }
  rg -q 'management\.endpoint\.health\.group\.readiness\.include=\*|readiness:[[:space:]]*$' "${config}" \
    || { echo "${module}: readiness must aggregate health contributors." >&2; exit 1; }
  rg -q 'management\.endpoint\.health\.group\.liveness\.include=livenessState|liveness:[[:space:]]*$' "${config}" \
    || { echo "${module}: liveness health group is missing." >&2; exit 1; }
  rg -q 'management\.endpoint\.health\.show-details=never|show-details: never' "${config}" \
    || { echo "${module}: health details must be hidden." >&2; exit 1; }
  rg -q 'management\.endpoint\.health\.show-components=never|show-components: never' "${config}" \
    || { echo "${module}: health components must be hidden." >&2; exit 1; }
done

rg -q 'CMD(-SHELL)? wget' "${ROOT_DIR}/Dockerfile" \
  && rg -Fq 'MANAGEMENT_SERVER_PORT:-9090}/actuator/health/readiness' "${ROOT_DIR}/Dockerfile" \
  || { echo "Docker healthcheck must use the private readiness probe." >&2; exit 1; }
rg -Fq 'HEALTHCHECK --interval=15s --timeout=3s --start-period=120s --retries=12' "${ROOT_DIR}/Dockerfile" \
  || { echo "Docker healthcheck must preserve the cold-start readiness budget." >&2; exit 1; }
if rg -n 'ports:.*9090|"9090:[^"]*"' "${ROOT_DIR}/docker-compose.yml" >/dev/null; then
  echo "Management port must not be published by canonical Compose." >&2
  exit 1
fi
for module in "${modules[@]}"; do
  rg -q "${module}:" "${ROOT_DIR}/docker-compose.yml" || continue
done
if [[ "$(rg -c '9090' "${ROOT_DIR}/docker-compose.yml")" -lt 17 ]]; then
  echo "Each application must expose the private management port to the Compose network." >&2
  exit 1
fi

echo "Actuator health/readiness configuration is valid."
