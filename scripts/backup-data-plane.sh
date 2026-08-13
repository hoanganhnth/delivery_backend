#!/usr/bin/env bash
set -euo pipefail

# Portable logical backup for every PostgreSQL database currently owned by a
# service. Run from a locked-down operator toolbox. For local rehearsals only,
# PG_CONTAINER may name an isolated PostgreSQL container.

: "${BACKUP_OUTPUT_DIR:?Set BACKUP_OUTPUT_DIR}"
: "${BACKUP_ENCRYPTION_PASSPHRASE_FILE:?Set BACKUP_ENCRYPTION_PASSPHRASE_FILE}"

readonly BACKUP_TIER="${BACKUP_TIER:-daily}"
readonly PGHOST="${PGHOST:-localhost}"
readonly PGPORT="${PGPORT:-5432}"
readonly PGUSER="${PGUSER:-postgres}"
readonly PG_CONTAINER="${PG_CONTAINER:-}"
readonly REQUIRE_KAFKA_METADATA="${REQUIRE_KAFKA_METADATA:-true}"
readonly ALLOW_MISSING_KAFKA_ACLS="${ALLOW_MISSING_KAFKA_ACLS:-false}"
readonly KAFKA_CONTAINER="${KAFKA_CONTAINER:-}"
readonly KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-}"
readonly OPENSSL_BIN="${OPENSSL_BIN:-openssl}"

case "$BACKUP_TIER" in
  daily|weekly|monthly) ;;
  *) printf 'BACKUP_TIER must be daily, weekly, or monthly.\n' >&2; exit 2 ;;
esac

if [[ ! -r "$BACKUP_ENCRYPTION_PASSPHRASE_FILE" ]]; then
  printf 'Backup passphrase file is not readable.\n' >&2
  exit 2
fi
if [[ "$(wc -c < "$BACKUP_ENCRYPTION_PASSPHRASE_FILE" | tr -d ' ')" -lt 20 ]]; then
  printf 'Backup passphrase must contain at least 20 bytes.\n' >&2
  exit 2
fi

command -v "$OPENSSL_BIN" >/dev/null
command -v shasum >/dev/null
mkdir -p "$BACKUP_OUTPUT_DIR"

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
mkdir -p "$work_dir/postgres" "$work_dir/kafka"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
artifact_base="delivery-data-${BACKUP_TIER}-${timestamp}.tar.gz.enc"
artifact_path="${BACKUP_OUTPUT_DIR%/}/${artifact_base}"

service_databases=(
  'auth-service:auth_db'
  'user-service:user_db'
  'restaurant-service:restaurant_db'
  'order-service:order_db'
  'delivery-service:delivery_db'
  'match-service:match_db'
  'shipper-service:shipper_db'
  'settlement-service:settlement_db'
  'notification-service:notification_service_db'
  'tracking-service:tracking_db'
  'livestream-service:livestream_db'
  'saga-orchestrator-service:saga_db'
  'promotion-service:promotion_db'
  'analytics-service:analytics_db'
  'flashsale-service:flashsale_db'
)

run_pg_tool() {
  local tool="$1"
  shift
  if [[ -n "$PG_CONTAINER" ]]; then
    command -v docker >/dev/null
    docker exec -i \
      -e "PGPASSWORD=${PGPASSWORD:-}" \
      "$PG_CONTAINER" "$tool" -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" "$@"
  else
    command -v "$tool" >/dev/null
    PGPASSWORD="${PGPASSWORD:-}" "$tool" -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" "$@"
  fi
}

cat > "$work_dir/manifest.txt" <<EOF
format_version=1
created_at_utc=${timestamp}
backup_tier=${BACKUP_TIER}
postgres_host=${PGHOST}
redis_included=false
redis_reason=Realtime GEO/freshness is rebuilt by authenticated publisher reconnect; restoring it could resurrect stale online state.
EOF

for mapping in "${service_databases[@]}"; do
  service="${mapping%%:*}"
  database="${mapping#*:}"
  dump_path="$work_dir/postgres/${service}--${database}.dump"
  printf '[BACKUP] %s (%s)\n' "$service" "$database"
  run_pg_tool pg_dump \
    --format=custom --compress=6 --no-owner --no-acl \
    --dbname "$database" > "$dump_path"
  printf 'postgres_database=%s:%s\n' "$service" "$database" >> "$work_dir/manifest.txt"
done

run_kafka_tool() {
  local tool="$1"
  shift
  if [[ -n "$KAFKA_CONTAINER" ]]; then
    docker exec -i "$KAFKA_CONTAINER" "$tool" \
      --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" "$@"
  else
    command -v "$tool" >/dev/null
    "$tool" --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" "$@"
  fi
}

if [[ -n "$KAFKA_BOOTSTRAP_SERVERS" ]]; then
  printf '[BACKUP] Kafka topic descriptions\n'
  run_kafka_tool "${KAFKA_TOPICS_BIN:-kafka-topics}" --describe \
    > "$work_dir/kafka/topics.txt"
  printf '[BACKUP] Kafka topic configurations\n'
  run_kafka_tool "${KAFKA_CONFIGS_BIN:-kafka-configs}" \
    --entity-type topics --all --describe > "$work_dir/kafka/topic-configs.txt"
  printf '[BACKUP] Kafka consumer offsets\n'
  run_kafka_tool "${KAFKA_CONSUMER_GROUPS_BIN:-kafka-consumer-groups}" \
    --all-groups --describe > "$work_dir/kafka/consumer-offsets.txt"
  printf '[BACKUP] Kafka ACLs\n'
  if ! run_kafka_tool "${KAFKA_ACLS_BIN:-kafka-acls}" --list \
      > "$work_dir/kafka/acls.txt"; then
    if [[ "$ALLOW_MISSING_KAFKA_ACLS" != "true" ]]; then
      printf 'Kafka ACL export failed; refusing incomplete production metadata backup.\n' >&2
      exit 1
    fi
    printf 'acl_export_status=authorizer-disabled-in-isolated-rehearsal\n' \
      > "$work_dir/kafka/acls.txt"
  fi
  printf 'kafka_metadata=included\n' >> "$work_dir/manifest.txt"
elif [[ "$REQUIRE_KAFKA_METADATA" == "true" ]]; then
  printf 'KAFKA_BOOTSTRAP_SERVERS is required unless REQUIRE_KAFKA_METADATA=false.\n' >&2
  exit 2
else
  printf 'kafka_metadata=skipped_for_isolated_rehearsal\n' >> "$work_dir/manifest.txt"
fi

(
  cd "$work_dir"
  find manifest.txt postgres kafka -type f -print | LC_ALL=C sort \
    | while IFS= read -r file; do shasum -a 256 "$file"; done \
    > checksums.sha256
)

plain_bundle="$work_dir/${artifact_base%.enc}"
tar -C "$work_dir" -czf "$plain_bundle" manifest.txt checksums.sha256 postgres kafka
"$OPENSSL_BIN" enc -aes-256-cbc -salt -pbkdf2 -iter 600000 -md sha256 \
  -in "$plain_bundle" -out "$artifact_path" \
  -pass "file:$BACKUP_ENCRYPTION_PASSPHRASE_FILE"
shasum -a 256 "$artifact_path" \
  | sed "s#  .*#  ${artifact_base}#" > "${artifact_path}.sha256"

printf 'Encrypted backup created: %s\n' "$artifact_path"
