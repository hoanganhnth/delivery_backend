#!/usr/bin/env bash
set -euo pipefail

# Compatibility entry point retained for local docs/bookmarks. The canonical
# fail-fast flow owns notification/current-offer, raw WebSocket and ledger proof.
printf '%s\n' \
  "scripts/test-order-flow.sh delegates to scripts/verify-mvp-cod-flow.sh"
exec bash "$(dirname "${BASH_SOURCE[0]}")/verify-mvp-cod-flow.sh" "$@"
