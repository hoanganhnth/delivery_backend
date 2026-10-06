#!/usr/bin/env bash
set -euo pipefail

# Packages host artifacts consumed by the Compose Dockerfile and writes a
# deterministic checksum manifest beside each JAR. Docker compares that
# manifest with the reactor source/POM build context, avoiding false stale
# errors from Maven reproducible JAR timestamps while still rejecting changes
# in a host or any shared/transitive module.

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

die() { printf 'Compose package: %s\n' "$*" >&2; exit 1; }

if (( $# == 0 )); then
  services=(config-server discovery-server auth-service user-service api-gateway web-bff-service)
else
  services=("$@")
fi

# Order keeps artifact order-service and resolves to order/boot through the canonical layout.
service_paths=()
for service in "${services[@]}"; do
  canonical="${service%-service}"
  # Consolidated services (including notification-service) package from <name>/boot.
  # The Saga orchestrator was repurposed in place as Dispatch; its artifact name is unchanged.
  [[ "$service" == "saga-orchestrator-service" ]] && canonical="dispatch"
  if [[ -f "${canonical}/boot/pom.xml" && -d "${canonical}/boot/src" ]]; then
    service_paths+=("${canonical}/boot")
  elif [[ -f "${service}/pom.xml" && -d "${service}/src" ]]; then
    service_paths+=("$service")
  else
    die "Unknown or non-packageable service: ${service}"
  fi
done

modules="$(IFS=,; printf '%s' "${service_paths[*]}")"
mvn -pl "$modules" -am -DskipTests package

write_manifest() {
  local service="$1" target="${service}/target/.docker-artifact-input.sha256" tmp
  compgen -G "${service}/target/*.jar" >/dev/null \
    || die "Missing packaged JAR for ${service}"
  tmp="${target}.tmp"
  {
    find . -type f \( -name pom.xml -o -path '*/src/*' \) \
      -not -path './docs/*' -print | LC_ALL=C sort | while IFS= read -r file; do
      shasum -a 256 "$file"
    done
  } | awk '{print $1}' | shasum -a 256 | awk '{print $1}' > "$tmp"
  mv "$tmp" "$target"
}

for service in "${service_paths[@]}"; do write_manifest "$service"; done
printf 'Compose package: fresh artifact manifests written for %s.\n' "${services[*]}"
