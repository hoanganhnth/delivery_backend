#!/usr/bin/env bash
set -euo pipefail

readonly POSTGRES_IMAGE="${POSTGRES_IMAGE:-postgres:16-alpine}"
readonly CONTAINER_NAME="${CONTAINER_NAME:-phase4-restore-rehearsal}"
readonly KAFKA_IMAGE="${KAFKA_IMAGE:-confluentinc/cp-kafka:7.4.0}"
readonly KAFKA_CONTAINER_NAME="${KAFKA_CONTAINER_NAME:-phase4-kafka-metadata-rehearsal}"
readonly REHEARSAL_RESTORE_PREFIX="phase4_restore_rehearsal_"
readonly RTO_SECONDS="${RTO_SECONDS:-7200}"
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

command -v docker >/dev/null
command -v openssl >/dev/null
if [[ ! "$CONTAINER_NAME" =~ ^phase4-restore-rehearsal ]]; then
  printf 'Rehearsal container name must start with phase4-restore-rehearsal.\n' >&2
  exit 2
fi
if docker ps -a --format '{{.Names}}' | grep -Fxq "$CONTAINER_NAME"; then
  printf 'Refusing to reuse existing container %s. Remove it explicitly first.\n' "$CONTAINER_NAME" >&2
  exit 2
fi
if docker ps -a --format '{{.Names}}' | grep -Fxq "$KAFKA_CONTAINER_NAME"; then
  printf 'Refusing to reuse existing Kafka container %s.\n' "$KAFKA_CONTAINER_NAME" >&2
  exit 2
fi

work_dir="$(mktemp -d)"
cleanup() {
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
  docker rm -f "$KAFKA_CONTAINER_NAME" >/dev/null 2>&1 || true
  rm -rf "$work_dir"
}
trap cleanup EXIT

passphrase_file="$work_dir/backup-passphrase"
openssl rand -hex 32 > "$passphrase_file"
chmod 600 "$passphrase_file"
mkdir -p "$work_dir/backups"

printf '[REHEARSAL] start disposable PostgreSQL container\n'
docker run -d --name "$CONTAINER_NAME" \
  -e POSTGRES_PASSWORD=phase4-rehearsal-only \
  -v "$REPO_DIR/docker/postgres/init-db.sql:/docker-entrypoint-initdb.d/00-init-db.sql:ro" \
  "$POSTGRES_IMAGE" >/dev/null

deadline=$((SECONDS + 60))
until docker logs "$CONTAINER_NAME" 2>&1 \
    | grep -Fq 'PostgreSQL init process complete; ready for start up.' \
  && docker exec "$CONTAINER_NAME" pg_isready -U postgres >/dev/null 2>&1; do
  if (( SECONDS >= deadline )); then
    printf 'Disposable PostgreSQL did not become ready.\n' >&2
    exit 1
  fi
  sleep 1
done
# The marker precedes the final postmaster by a few milliseconds. Require two
# successful probes around a short interval so fixture writes cannot race the
# temporary init postmaster shutdown.
sleep 1
docker exec "$CONTAINER_NAME" pg_isready -U postgres >/dev/null

printf '[REHEARSAL] create representative critical fixture\n'
docker exec -i -e PGPASSWORD=phase4-rehearsal-only "$CONTAINER_NAME" \
  psql -X -v ON_ERROR_STOP=1 -U postgres -d postgres \
  < "$SCRIPT_DIR/fixtures/phase4-critical-data.sql"

printf '[REHEARSAL] start disposable KRaft broker and provision metadata fixture\n'
docker run -d --name "$KAFKA_CONTAINER_NAME" \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  -e KAFKA_AUTO_CREATE_TOPICS_ENABLE=true \
  -e CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qk \
  "$KAFKA_IMAGE" >/dev/null
kafka_deadline=$((SECONDS + 90))
until docker exec "$KAFKA_CONTAINER_NAME" \
    kafka-broker-api-versions --bootstrap-server localhost:9092 >/dev/null 2>&1; do
  if (( SECONDS >= kafka_deadline )); then
    printf 'Disposable Kafka did not become ready.\n' >&2
    exit 1
  fi
  sleep 2
done
docker exec "$KAFKA_CONTAINER_NAME" kafka-topics \
  --bootstrap-server localhost:9092 --create --if-not-exists \
  --topic shipper.location-updated --partitions 3 --replication-factor 1 \
  --config retention.ms=86400000 >/dev/null

PG_CONTAINER="$CONTAINER_NAME" PGHOST=127.0.0.1 PGUSER=postgres \
PGPASSWORD=phase4-rehearsal-only OUTPUT_FILE="$work_dir/before.state" \
  "$SCRIPT_DIR/verify-restored-critical-data.sh"

printf '[REHEARSAL] create encrypted backup and checksums\n'
BACKUP_OUTPUT_DIR="$work_dir/backups" \
BACKUP_ENCRYPTION_PASSPHRASE_FILE="$passphrase_file" \
BACKUP_TIER=daily PG_CONTAINER="$CONTAINER_NAME" PGHOST=127.0.0.1 \
PGUSER=postgres PGPASSWORD=phase4-rehearsal-only \
KAFKA_CONTAINER="$KAFKA_CONTAINER_NAME" KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
REQUIRE_KAFKA_METADATA=true \
ALLOW_MISSING_KAFKA_ACLS=true \
  "$SCRIPT_DIR/backup-data-plane.sh"

backup_file="$(find "$work_dir/backups" -type f -name '*.tar.gz.enc' -print -quit)"
if [[ -z "$backup_file" ]]; then
  printf 'Backup artifact was not created.\n' >&2
  exit 1
fi

printf '[REHEARSAL] inspect encrypted Kafka metadata payload\n'
metadata_bundle="$work_dir/metadata-bundle.tar.gz"
metadata_dir="$work_dir/metadata"
mkdir -p "$metadata_dir"
openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -md sha256 \
  -in "$backup_file" -out "$metadata_bundle" -pass "file:$passphrase_file"
tar -C "$metadata_dir" -xzf "$metadata_bundle"
grep -Fq 'shipper.location-updated' "$metadata_dir/kafka/topics.txt"
grep -Fq 'retention.ms=86400000' "$metadata_dir/kafka/topic-configs.txt"
test -f "$metadata_dir/kafka/consumer-offsets.txt"
grep -Fq 'acl_export_status=authorizer-disabled-in-isolated-rehearsal' \
  "$metadata_dir/kafka/acls.txt"

printf '[REHEARSAL] prove outer checksum rejects a modified artifact\n'
tampered_file="${backup_file}.tampered"
cp "$backup_file" "$tampered_file"
printf 'tamper' >> "$tampered_file"
expected_hash="$(awk '{print $1}' "${backup_file}.sha256")"
printf '%s  %s\n' "$expected_hash" "$(basename "$tampered_file")" \
  > "${tampered_file}.sha256"
if BACKUP_FILE="$tampered_file" \
  BACKUP_ENCRYPTION_PASSPHRASE_FILE="$passphrase_file" \
  RESTORE_PREFIX="$REHEARSAL_RESTORE_PREFIX" RESTORE_CONFIRMATION=DROP_ISOLATED_DATABASES \
  PG_CONTAINER="$CONTAINER_NAME" PGHOST=127.0.0.1 PGUSER=postgres \
  PGPASSWORD=phase4-rehearsal-only \
  "$SCRIPT_DIR/restore-data-plane-isolated.sh" >/dev/null 2>&1; then
  printf 'Tampered backup unexpectedly passed integrity verification.\n' >&2
  exit 1
fi

printf '[REHEARSAL] delete isolated source databases\n'
service_databases=(
  auth_db user_db restaurant_db order_db delivery_db match_db shipper_db settlement_db
  notification_service_db tracking_db livestream_db saga_db promotion_db analytics_db flashsale_db
)
for database in "${service_databases[@]}"; do
  docker exec -i -e PGPASSWORD=phase4-rehearsal-only "$CONTAINER_NAME" \
    dropdb -h 127.0.0.1 -U postgres --if-exists --force "$database"
done

printf '[REHEARSAL] restore every service database into isolated names\n'
restore_started="$(date +%s)"
BACKUP_FILE="$backup_file" \
BACKUP_ENCRYPTION_PASSPHRASE_FILE="$passphrase_file" \
RESTORE_PREFIX="$REHEARSAL_RESTORE_PREFIX" RESTORE_CONFIRMATION=DROP_ISOLATED_DATABASES \
PG_CONTAINER="$CONTAINER_NAME" PGHOST=127.0.0.1 PGUSER=postgres \
PGPASSWORD=phase4-rehearsal-only \
  "$SCRIPT_DIR/restore-data-plane-isolated.sh"
restore_elapsed=$(( $(date +%s) - restore_started ))

DATABASE_PREFIX="$REHEARSAL_RESTORE_PREFIX" PG_CONTAINER="$CONTAINER_NAME" \
PGHOST=127.0.0.1 PGUSER=postgres PGPASSWORD=phase4-rehearsal-only \
OUTPUT_FILE="$work_dir/after.state" \
  "$SCRIPT_DIR/verify-restored-critical-data.sh"

if ! cmp -s "$work_dir/before.state" "$work_dir/after.state"; then
  printf 'Critical state differs after restore.\n' >&2
  diff -u "$work_dir/before.state" "$work_dir/after.state" >&2 || true
  exit 1
fi
if (( restore_elapsed > RTO_SECONDS )); then
  printf 'Restore took %ss, exceeding target %ss.\n' "$restore_elapsed" "$RTO_SECONDS" >&2
  exit 1
fi

printf '[REHEARSAL] smoke-check order/delivery/settlement/projection chain\n'
docker exec -i -e PGPASSWORD=phase4-rehearsal-only "$CONTAINER_NAME" \
  psql -X -v ON_ERROR_STOP=1 -U postgres \
  -d "${REHEARSAL_RESTORE_PREFIX}settlement_db" -At -c \
  "SELECT CASE WHEN count(*) = 1 THEN 'ok' ELSE 'failed' END FROM settlement_receipts WHERE order_id=101 AND delivery_id=201" \
  | grep -Fxq ok
docker exec -i -e PGPASSWORD=phase4-rehearsal-only "$CONTAINER_NAME" \
  psql -X -v ON_ERROR_STOP=1 -U postgres \
  -d "${REHEARSAL_RESTORE_PREFIX}notification_service_db" -At -c \
  "SELECT CASE WHEN count(*) = 1 THEN 'ok' ELSE 'failed' END FROM notifications WHERE related_entity_id=101 AND deduplication_key='delivery-completed:201:1001'" \
  | grep -Fxq ok

printf 'Backup/restore rehearsal passed: 15 service databases restored in %ss; critical fingerprints, uniqueness and smoke flow match.\n' \
  "$restore_elapsed"
