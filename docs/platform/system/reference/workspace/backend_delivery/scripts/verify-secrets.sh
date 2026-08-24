#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# Scan tracked source/configuration only. Ignored operator secret files are not
# read, printed or accepted as evidence. The patterns target credentials rather
# than harmless `${VARIABLE:}` placeholders.
if git ls-files | rg '(^|/)(private|public|jwt).*\.pem$|firebase-service-account\.json$' >/dev/null; then
  echo "Tracked key or Firebase credential file detected; inject it through a secret manager instead." >&2
  exit 1
fi

if git grep -n -I -F -e '-----BEGIN' \
  -- ':!docs/**' ':!scripts/gen-keys.sh' \
  ':!scripts/verify-secrets.sh' \
  ':!auth-service/src/main/java/com/delivery/auth_service/service/TokenService.java' >/dev/null \
  || git grep -n -I -E -e 'AIza[0-9A-Za-z_-]{30,}' -e 'AKIA[0-9A-Z]{16}' \
    -- ':!docs/**' ':!scripts/gen-keys.sh' >/dev/null; then
  echo "Potential real credential material detected in tracked runtime source." >&2
  exit 1
fi

if git grep -n -I -E -e \
  '(^|[[:space:]])(INTERNAL_SECRET|POSTGRES_PASSWORD|SPRING_DATASOURCE_PASSWORD):[[:space:]]+[^${[:space:]][^[:space:]]*' \
  -- 'docker-compose*.yml' >/dev/null; then
  echo "A runtime secret is encoded directly in Compose; use a file-backed secret instead." >&2
  exit 1
fi

if ! rg -q '^INTERNAL_SECRET_FILE=\./\.secrets/internal-secret$' .env.example \
    || ! rg -q '^DB_PASSWORD_FILE=\./\.secrets/db-password$' .env.example; then
  echo ".env.example must contain only secret file placeholders." >&2
  exit 1
fi

printf '%s\n' "Secret scan passed."
