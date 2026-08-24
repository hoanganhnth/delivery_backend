#!/usr/bin/env bash
set -euo pipefail

: "${BACKUP_FILE:?Set BACKUP_FILE to an encrypted backup artifact}"
: "${BACKUP_ENCRYPTION_PASSPHRASE_FILE:?Set BACKUP_ENCRYPTION_PASSPHRASE_FILE}"
: "${RESTORE_PREFIX:?Set RESTORE_PREFIX (must start with phase4_restore_)}"
: "${RESTORE_CONFIRMATION:?Set RESTORE_CONFIRMATION=DROP_ISOLATED_DATABASES}"

readonly PGHOST="${PGHOST:-localhost}"
readonly PGPORT="${PGPORT:-5432}"
readonly PGUSER="${PGUSER:-postgres}"
readonly PG_CONTAINER="${PG_CONTAINER:-}"
readonly OPENSSL_BIN="${OPENSSL_BIN:-openssl}"

if [[ ! "$RESTORE_PREFIX" =~ ^phase4_restore_[a-z0-9_]+_$ ]]; then
  printf 'RESTORE_PREFIX must match phase4_restore_[a-z0-9_]+_ (including trailing underscore).\n' >&2
  exit 2
fi
if [[ "$RESTORE_CONFIRMATION" != "DROP_ISOLATED_DATABASES" ]]; then
  printf 'Refusing to drop restore targets without explicit isolated confirmation.\n' >&2
  exit 2
fi
if [[ ! -r "$BACKUP_FILE" || ! -r "${BACKUP_FILE}.sha256" ]]; then
  printf 'Encrypted backup and its .sha256 sidecar must both be readable.\n' >&2
  exit 2
fi

command -v "$OPENSSL_BIN" >/dev/null
command -v shasum >/dev/null

(
  cd "$(dirname "$BACKUP_FILE")"
  shasum -a 256 -c "$(basename "${BACKUP_FILE}.sha256")"
)

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
plain_bundle="$work_dir/backup.tar.gz"
"$OPENSSL_BIN" enc -d -aes-256-cbc -pbkdf2 -iter 600000 -md sha256 \
  -in "$BACKUP_FILE" -out "$plain_bundle" \
  -pass "file:$BACKUP_ENCRYPTION_PASSPHRASE_FILE"
tar -C "$work_dir" -xzf "$plain_bundle"
(
  cd "$work_dir"
  shasum -a 256 -c checksums.sha256
)

run_pg_tool() {
  local tool="$1"
  shift
  if [[ -n "$PG_CONTAINER" ]]; then
    docker exec -i \
      -e "PGPASSWORD=${PGPASSWORD:-}" \
      "$PG_CONTAINER" "$tool" -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" "$@"
  else
    command -v "$tool" >/dev/null
    PGPASSWORD="${PGPASSWORD:-}" "$tool" -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" "$@"
  fi
}

find "$work_dir/postgres" -type f -name '*.dump' | LC_ALL=C sort \
  > "$work_dir/dumps.list"
while IFS= read -r dump_path <&3; do
  dump_name="$(basename "$dump_path" .dump)"
  source_database="${dump_name#*--}"
  target_database="${RESTORE_PREFIX}${source_database}"
  if [[ "$target_database" == "$source_database" || ! "$target_database" =~ ^phase4_restore_ ]]; then
    printf 'Unsafe restore target rejected: %s\n' "$target_database" >&2
    exit 2
  fi
  printf '[RESTORE] %s -> %s\n' "$source_database" "$target_database"
  run_pg_tool dropdb --if-exists --force "$target_database"
  run_pg_tool createdb "$target_database"
  run_pg_tool pg_restore --exit-on-error --no-owner --no-acl \
    --dbname "$target_database" < "$dump_path"
done 3< "$work_dir/dumps.list"

printf 'Restore completed into isolated prefix %s. Kafka offsets were captured for comparison only and were not mutated.\n' \
  "$RESTORE_PREFIX"
