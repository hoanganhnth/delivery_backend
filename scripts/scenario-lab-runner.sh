#!/usr/bin/env bash
# =============================================================================
# Delivery Scenario Lab — CLI client for simulator-service
# =============================================================================
set -euo pipefail

readonly API_BASE="${SIMULATOR_API_BASE:-http://127.0.0.1:8100/api/simulator}"
readonly SCENARIO_FILE="${1:?Usage: scenario-lab-runner.sh path/to/scenario.json}"
readonly API_TOKEN="${SIMULATOR_API_TOKEN:-}"
readonly ADMIN_TOKEN="${SIMULATOR_ADMIN_TOKEN:-}"
readonly POLL_SECONDS="${SIMULATOR_POLL_SECONDS:-2}"
readonly CLEANUP="${SIMULATOR_CLEANUP:-false}"

if [[ ! -f "$SCENARIO_FILE" ]]; then
  echo "Scenario file does not exist: $SCENARIO_FILE" >&2
  exit 2
fi
command -v jq >/dev/null || { echo "jq is required" >&2; exit 2; }

headers=(-H 'Accept: application/json' -H 'Content-Type: application/json')
if [[ -n "$API_TOKEN" ]]; then
  headers+=(-H "X-Simulator-Token: $API_TOKEN")
fi
if [[ -n "$ADMIN_TOKEN" ]]; then
  headers+=(-H "Authorization: Bearer $ADMIN_TOKEN")
fi

step() {
  printf '\033[1;34m[SCENARIO LAB]\033[0m %s\n' "$1"
}

request() {
  curl --fail-with-body --silent --show-error "${headers[@]}" "$@"
}

step "Validate scenario: $SCENARIO_FILE"
validation="$(request -X POST "$API_BASE/validate" --data-binary @"$SCENARIO_FILE")"
if [[ "$(jq -r '.valid // false' <<<"$validation")" != "true" ]]; then
  jq -r '.errors[]? // "Scenario is invalid"' <<<"$validation" >&2
  exit 1
fi

step "Start real Gateway run"
run="$(request -X POST "$API_BASE/runs" --data-binary @"$SCENARIO_FILE")"
run_id="$(jq -er '.runId' <<<"$run")"
echo "Run ID: $run_id"

cleanup() {
  if [[ "$CLEANUP" == "true" ]]; then
    request -X DELETE "$API_BASE/runs/$run_id" >/dev/null || true
  fi
}
trap cleanup EXIT

while true; do
  snapshot="$(request -X GET "$API_BASE/runs/$run_id" -H 'Content-Type: application/json')"
  status="$(jq -r '.status // "UNKNOWN"' <<<"$snapshot")"
  order_status="$(jq -r '.orderStatus // "-"' <<<"$snapshot")"
  delivery_status="$(jq -r '.deliveryStatus // "-"' <<<"$snapshot")"
  printf 'status=%s order=%s delivery=%s\n' "$status" "$order_status" "$delivery_status"

  case "$status" in
    PASSED|PARTIAL|FAILED|ABORTED)
      jq '{runId,status,orderId,deliveryId,orderStatus,deliveryStatus,assignedShipperId,assertions}' <<<"$snapshot"
      [[ "$status" == "FAILED" || "$status" == "ABORTED" ]] && exit 1
      [[ "$status" == "PARTIAL" ]] && exit 2
      exit 0
      ;;
  esac
  sleep "$POLL_SECONDS"
done
