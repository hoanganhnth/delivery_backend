#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INVENTORY="${ROOT_DIR}/docs/http-api-inventory.md"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TEMP_DIR}"' EXIT

mapping_pattern='^[[:space:]]*@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|RequestMapping[[:space:]]*\([[:space:]]*value)'

actual_count="$(rg --glob '*Controller.java' "${mapping_pattern}" \
  "${ROOT_DIR}"/*/src/main/java | wc -l | tr -d ' ')"
inventory_count="$(awk -F'|' '
  $2 ~ /^[[:space:]]*[a-z0-9-]+-service[[:space:]]*$/ { count++ }
  END { print count + 0 }
' "${INVENTORY}")"

if [[ "${actual_count}" != "${inventory_count}" ]]; then
  echo "HTTP inventory has ${inventory_count} rows but source has ${actual_count} mapped methods." >&2
  exit 1
fi
if ! rg -Fq "hiện có **${actual_count} method**." "${INVENTORY}"; then
  echo "HTTP inventory summary count is stale; expected ${actual_count} methods." >&2
  exit 1
fi

awk -F'|' '
  $2 ~ /^[[:space:]]*[a-z0-9-]+-service[[:space:]]*$/ {
    service=$2; controller=$3; handler=$(NF-1)
    gsub(/^ +| +$/, "", service)
    gsub(/^ +| +$/, "", controller)
    gsub(/^ +| +$/, "", handler)
    gsub(/`/, "", handler)
    print service "|" controller "|" handler
  }
' "${INVENTORY}" > "${TEMP_DIR}/inventory-handlers"

while IFS='|' read -r service controller handler; do
  controller_file="$(find "${ROOT_DIR}/${service}/src/main/java" \
    -name "${controller}.java" -print -quit)"
  if [[ -z "${controller_file}" ]]; then
    echo "HTTP inventory references missing controller ${service}/${controller}." >&2
    exit 1
  fi
  if ! rg -q "[[:space:]]${handler}[[:space:]]*\(" "${controller_file}"; then
    echo "HTTP inventory assigns missing handler ${service}/${controller}.${handler}." >&2
    exit 1
  fi
done < "${TEMP_DIR}/inventory-handlers"

echo "HTTP API inventory is aligned with ${actual_count} mapped controller methods."
