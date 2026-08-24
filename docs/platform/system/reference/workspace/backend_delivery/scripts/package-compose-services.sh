#!/usr/bin/env bash
set -euo pipefail

# Packages host artifacts consumed by the Compose Dockerfile and writes a
# deterministic checksum manifest beside each JAR. Docker compares that
# manifest with its own source/POM build context, avoiding false stale errors
# from Maven reproducible JAR timestamps while still rejecting changed input.

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

die() { printf 'Compose package: %s\n' "$*" >&2; exit 1; }

if (( $# == 0 )); then
  services=(config-server discovery-server auth-service user-service api-gateway)
else
  services=("$@")
fi

for service in "${services[@]}"; do
  [[ -f "${service}/pom.xml" && -d "${service}/src" ]] \
    || die "Unknown or non-packageable service: ${service}"
done

modules="$(IFS=,; printf '%s' "${services[*]}")"
mvn -pl "$modules" -am -DskipTests package

write_manifest() {
  local service="$1" target="${service}/target/.docker-artifact-input.sha256" tmp
  compgen -G "${service}/target/*.jar" >/dev/null \
    || die "Missing packaged JAR for ${service}"
  tmp="${target}.tmp"
  {
    shasum -a 256 pom.xml "${service}/pom.xml"
    find "${service}/src" -type f -print | LC_ALL=C sort | while IFS= read -r file; do
      shasum -a 256 "$file"
    done
  } | awk '{print $1}' | shasum -a 256 | awk '{print $1}' > "$tmp"
  mv "$tmp" "$target"
}

for service in "${services[@]}"; do write_manifest "$service"; done
printf 'Compose package: fresh artifact manifests written for %s.\n' "${services[*]}"
