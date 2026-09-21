#!/usr/bin/env bash
# Verifies deterministic contact enrichment and the local backfill contract.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CATALOG_FILE="${CATALOG_FILE:-$WORKSPACE_DIR/../data/catalog/hanoi-catalog.json}"
if [[ ! -f "$CATALOG_FILE" ]]; then
  CATALOG_FILE="$SCRIPT_DIR/fixtures/hanoi-restaurant-contact-manifest.json"
fi

node --test "$SCRIPT_DIR/workspace/hanoi-contact-data.test.mjs"

summary="$(node "$SCRIPT_DIR/workspace/enrich-hanoi-contact-data.mjs" --catalog "$CATALOG_FILE")"
jq -e '
  .restaurants == 485 and
  .sourceBackedAddresses == 63 and
  .generatedAddresses == 422 and
  .uniquePhones == 485
' <<<"$summary" >/dev/null

if [[ "$CATALOG_FILE" == *hanoi-catalog.json ]]; then
  node "$SCRIPT_DIR/workspace/validate-data-catalog.mjs" "$CATALOG_FILE" >/dev/null
fi

bash -n "$SCRIPT_DIR/backfill-hanoi-catalog-contact-data.sh"
grep -Fq 'addressLat' "$SCRIPT_DIR/backfill-hanoi-catalog-contact-data.sh"
grep -Fq 'addressLng' "$SCRIPT_DIR/backfill-hanoi-catalog-contact-data.sh"
grep -Fq 'phone' "$SCRIPT_DIR/seed-realistic-catalog.sh"

echo "✅ Hanoi contact fixture: deterministic enrichment, validation and backfill contract PASS"
