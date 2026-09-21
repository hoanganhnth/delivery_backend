#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

exec node "${ROOT_DIR}/docs/platform/system/api/verify-http-api-inventory.mjs"
