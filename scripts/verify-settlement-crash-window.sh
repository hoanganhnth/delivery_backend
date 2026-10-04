#!/usr/bin/env bash
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Only owned disposable resources are touched. Never uses the canonical Compose stack.
readonly OWNER="settlement-crash-$(date +%s)-$$-${RANDOM}"
readonly NETWORK="$OWNER-net"
readonly POSTGRES_CONTAINER="$OWNER-pg"
readonly KAFKA_CONTAINER="$OWNER-kafka"
readonly OWNERSHIP_LABEL="delivery.settlement-crash.owner=$OWNER"

readonly RUN_ID="${SETTLEMENT_CRASH_RUN_ID:-$(date +%Y%m%d%H%M%S)-$$}"
readonly SAFE_RUN_ID="${RUN_ID//-/_}"
readonly TEST_DATABASE="settlement_crash_${SAFE_RUN_ID}"
readonly TEST_TOPIC="settlement.crash-window.${RUN_ID}"
readonly TEST_GROUP="settlement-crash-window-${RUN_ID}"
readonly CRASH_CONTAINER="$OWNER-crash"
readonly RECOVERY_CONTAINER="$OWNER-recovery"

readonly EVENT_ID="bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
readonly DELIVERY_ID=990002
readonly ORDER_ID=990102
readonly RESTAURANT_ID=990012
readonly SHIPPER_ID=990023
readonly TIMEOUT_SECONDS="${SETTLEMENT_CRASH_TIMEOUT_SECONDS:-120}"
readonly KEEP_TEST_ARTIFACTS="${KEEP_SETTLEMENT_CRASH_ARTIFACTS:-false}"
readonly PROBE_CLASS='com.delivery.settlement_service.listener.DeliveryCompletedEventListener$1'
readonly PROBE_METHOD='afterCommit'
# Select by current source layout, never by newest/stale artifact glob across layouts.
if [[ -n "${SETTLEMENT_CRASH_JAR:-}" ]]; then
  jar_candidate="$SETTLEMENT_CRASH_JAR"
elif [[ -f settlement/boot/pom.xml ]]; then
  jar_candidate='settlement/boot/target/settlement-service-0.0.1-SNAPSHOT.jar'
else
  jar_candidate='settlement-service/target/settlement-service-0.0.1-SNAPSHOT.jar'
fi
JAR_PATH="$(python3 - "$jar_candidate" <<'PYJAR'
import pathlib, sys
jar = pathlib.Path(sys.argv[1]).resolve()
if not jar.is_file():
    sys.exit(f'Missing {jar}; clean-package the current Settlement service first.')
# Walk the actual local Maven runtime dependency graph, including shared starters.
# Test/provided dependencies are not packaged runtime inputs.
import xml.etree.ElementTree as ET
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
projects = {}
for pom in pathlib.Path('.').rglob('pom.xml'):
    if any(part in {'target', '.git', 'docs'} for part in pom.parts):
        continue
    document = ET.parse(pom).getroot()
    artifact = document.findtext('m:artifactId', namespaces=ns)
    if artifact:
        projects.setdefault(artifact, []).append((pom, document))
boot = pathlib.Path('settlement/boot/pom.xml') if pathlib.Path('settlement/boot/pom.xml').is_file() else pathlib.Path('settlement-service/pom.xml')
inputs = {pathlib.Path('pom.xml')}
visited = set()
def visit(pom):
    if pom in visited:
        return
    visited.add(pom)
    inputs.add(pom)
    root = pom.parent
    inputs.update(p for p in (root / 'src/main').rglob('*') if p.is_file())
    document = ET.parse(pom).getroot()
    parent = document.find('m:parent', ns)
    if parent is not None:
        relative = parent.findtext('m:relativePath', default='../pom.xml', namespaces=ns)
        parent_pom = (root / relative).resolve() if relative else None
        if parent_pom and parent_pom.is_file():
            visit(parent_pom)
    dependencies = document.findall('m:dependencies/m:dependency', ns)
    dependencies += [d for d in document.findall('m:dependencyManagement/m:dependencies/m:dependency', ns)
                     if d.findtext('m:scope', namespaces=ns) == 'import']
    for dependency in dependencies:
        if dependency.findtext('m:scope', default='compile', namespaces=ns) in {'test', 'provided'}:
            continue
        group = dependency.findtext('m:groupId', default='', namespaces=ns)
        artifact = dependency.findtext('m:artifactId', namespaces=ns)
        if group != 'com.delivery':
            continue
        if artifact not in projects:
            sys.exit(f'Missing local runtime dependency source {artifact}; cannot prove package freshness.')
        candidates = projects[artifact]
        if len(candidates) != 1:
            sys.exit(f'Ambiguous local runtime dependency {artifact}; remove retired duplicate POMs before proof.')
        visit(candidates[0][0])
if boot.is_file():
    visit(boot)
stale = [str(p) for p in inputs if p.stat().st_mtime_ns > jar.stat().st_mtime_ns]
if stale:
    sys.exit('Stale Settlement JAR; clean-package current sources first: ' + ', '.join(stale[:5]))
print(jar)
PYJAR
)"
readonly JAR_PATH

if [[ ! "$RUN_ID" =~ ^[a-zA-Z0-9][a-zA-Z0-9-]{0,39}$ ]]; then
  printf 'SETTLEMENT_CRASH_RUN_ID contains unsupported characters: %s\n' "$RUN_ID" >&2
  exit 1
fi

if [[ ! "$TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  printf '%s\n' 'SETTLEMENT_CRASH_TIMEOUT_SECONDS must be a positive integer.' >&2
  exit 1
fi

for command in docker grep awk python3; do
  command -v "$command" >/dev/null
done

if [[ -n "${JAVA_HOME:-}" ]]; then
  readonly JAVA_BIN="$JAVA_HOME/bin/java"
else
  readonly JAVA_BIN="$(command -v java)"
fi

if ! docker info >/dev/null 2>&1; then
  printf '%s\n' 'Docker daemon is unavailable; crash-window proof was not executed.' >&2
  exit 1
fi

[[ -f "$JAR_PATH" ]] || {
  printf 'Missing %s; package settlement-service with JDK 17 first.\n' "$JAR_PATH" >&2
  exit 1
}
python3 - "$JAR_PATH" <<'PYCLASS'
import io, sys, zipfile
name = 'com/delivery/settlement_service/listener/DeliveryCompletedEventListener$1.class'
with zipfile.ZipFile(sys.argv[1]) as boot:
    found = 'BOOT-INF/classes/' + name in boot.namelist()
    for entry in boot.namelist():
        if entry.startswith('BOOT-INF/lib/') and entry.endswith('.jar'):
            with zipfile.ZipFile(io.BytesIO(boot.read(entry))) as library:
                found |= name in library.namelist()
    if not found:
        sys.exit('Settlement executable JAR does not contain the afterCommit callback class.')
PYCLASS

probe_pid=''
probe_log="$(mktemp)"
owned_containers=()
network_created=false

cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM
  set +u # Bash 3.2 treats an empty tracked-resource array as unset.

  if [[ -n "$probe_pid" ]]; then
    kill "$probe_pid" >/dev/null 2>&1 || true
  fi
  if (( exit_code != 0 )); then
    for container in "${owned_containers[@]}"; do
      docker logs --tail 100 "$container" >&2 || true
    done
  fi
  if [[ "$KEEP_TEST_ARTIFACTS" != 'true' ]]; then
    for container in "${owned_containers[@]}"; do
      if [[ "$(docker inspect --format '{{index .Config.Labels "delivery.settlement-crash.owner"}}' "$container" 2>/dev/null || true)" == "$OWNER" ]]; then
        docker rm -fv "$container" >/dev/null 2>&1 || true
      fi
    done
    if [[ "$network_created" == true ]]; then
      docker network rm "$NETWORK" >/dev/null 2>&1 || true
    fi
  else
    printf 'Retained owned fixture network %s; containers: %s\n' "$NETWORK" "${owned_containers[*]}" >&2
  fi
  rm -f "$probe_log"
  exit "$exit_code"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

wait_for_container_log() {
  local container="$1"
  local pattern="$2"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    if docker logs "$container" 2>&1 | grep -F "$pattern" >/dev/null; then
      return 0
    fi
    if [[ "$(docker inspect --format '{{.State.Status}}' "$container" 2>/dev/null || true)" \
        != 'running' ]]; then
      docker logs --tail 160 "$container" >&2 || true
      return 1
    fi
    sleep 1
  done
  docker logs --tail 160 "$container" >&2 || true
  return 1
}

wait_for_probe_log() {
  local pattern="$1"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    if grep -F "$pattern" "$probe_log" >/dev/null; then
      return 0
    fi
    if [[ -n "$probe_pid" ]] && ! kill -0 "$probe_pid" >/dev/null 2>&1; then
      cat "$probe_log" >&2
      return 1
    fi
    sleep 1
  done
  cat "$probe_log" >&2
  return 1
}

# Killing the local Docker CLI also bounds hangs in daemon/coordinator calls.
# CLI requests themselves additionally use Kafka's server-side timeout where available.
bounded_command() {
  local limit="$1"
  shift
  python3 -c '
import os, signal, subprocess, sys
process = subprocess.Popen(sys.argv[2:], start_new_session=True)
try:
    sys.exit(process.wait(timeout=float(sys.argv[1])))
except subprocess.TimeoutExpired:
    os.killpg(process.pid, signal.SIGKILL)
    process.wait()
    sys.exit(124)
' "$limit" "$@"
}

group_field() {
  local column="$1" remaining="${2:-10}" output
  # Coordinator discovery is transient during recovery. An unavailable observation
  # is empty, never evidence of a committed offset and never aborts the retry loop.
  if ! output="$(bounded_command "$remaining" docker exec "$KAFKA_CONTAINER" kafka-consumer-groups \
      --bootstrap-server kafka:9092 --group "$TEST_GROUP" --describe --timeout 5000 2>/dev/null)"; then
    return 0
  fi
  printf '%s\n' "$output" | awk -v topic="$TEST_TOPIC" -v column="$column" \
      '$2 == topic && $3 == "0" && !found { print $column; found=1 }'
}
wait_for_group_assignment() {
  local deadline=$((SECONDS + TIMEOUT_SECONDS)) offset remaining
  while (( SECONDS < deadline )); do
    remaining=$((deadline - SECONDS))
    (( remaining <= 10 )) || remaining=10
    offset="$(group_field 5 "$remaining")"
    [[ "$offset" == '0' ]] && return 0
    sleep 1
  done
  printf '%s\n' 'Consumer group did not acquire the empty fixture topic before deadline.' >&2
  return 1
}

database_invariants_hold() {
  [[ "$(docker exec -i "$POSTGRES_CONTAINER" psql -U postgres -d "$TEST_DATABASE" -At -v ON_ERROR_STOP=1 \
    -c "SELECT ((SELECT count(*) FROM settlement_receipts WHERE event_id = '$EVENT_ID') = 1
            AND (SELECT count(*) FROM transactions WHERE order_id = $ORDER_ID) = 4
            AND (SELECT deposit_balance FROM balances
                 WHERE entity_id = $SHIPPER_ID AND entity_type = 'SHIPPER') = 0
            AND (SELECT total_cod_collected FROM balances
                 WHERE entity_id = $SHIPPER_ID AND entity_type = 'SHIPPER') = 120000)::int;")" == '1' ]]
}

ledger_snapshot() {
  # Full row values, including timestamps/fingerprints, must remain identical on replay.
  docker exec "$POSTGRES_CONTAINER" psql -U postgres -d "$TEST_DATABASE" -At -v ON_ERROR_STOP=1 \
    -c "SELECT 'receipt:' || row_to_json(r)::text FROM settlement_receipts r ORDER BY row_to_json(r)::text;
        SELECT 'transaction:' || row_to_json(t)::text FROM transactions t ORDER BY row_to_json(t)::text;
        SELECT 'balance:' || row_to_json(b)::text FROM balances b ORDER BY row_to_json(b)::text;"
}

owned_run() {
  local name="$1"
  shift
  owned_containers+=("$name")
  docker run -d --name "$name" --label "$OWNERSHIP_LABEL" --network "$NETWORK" "$@" >/dev/null
}
wait_for_dependency() {
  local name="$1"
  shift
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  local remaining
  while (( SECONDS < deadline )); do
    remaining=$((deadline - SECONDS))
    (( remaining <= 10 )) || remaining=10
    bounded_command "$remaining" docker exec "$name" "$@" >/dev/null 2>&1 && return 0
    sleep 1
  done
  docker logs --tail 100 "$name" >&2
  return 1
}
start_application() {
  local name="$1"
  shift
  owned_run "$name" "$@" \
    --mount "type=bind,source=$JAR_PATH,target=/app.jar,readonly" \
    -e CONFIG_SERVER_IMPORT=optional:configtree:/run/secrets/ \
    -e SPRING_CLOUD_CONFIG_ENABLED=false -e SERVICE_DISCOVERY_ENABLED=false \
    -e "SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/$TEST_DATABASE" \
    -e SPRING_DATASOURCE_USERNAME=postgres -e SPRING_DATASOURCE_PASSWORD=fixture-only \
    -e SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
    -e "SPRING_KAFKA_CONSUMER_GROUP_ID=$TEST_GROUP" \
    -e "SETTLEMENT_DELIVERY_COMPLETED_TOPIC=$TEST_TOPIC" \
    -e KAFKA_RETRY_AUTO_CREATE_TOPICS=true \
    -e SPRING_TASK_SCHEDULING_ENABLED=false \
    -e INTERNAL_SECRET=settlement-crash-fixture-only \
    "${SETTLEMENT_CRASH_JAVA_IMAGE:-eclipse-temurin:17-jre}" java -Xmx384m -Xms256m -jar /app.jar
}
printf '[SETTLEMENT-CRASH] Owned fixture %s; packaged JAR %s\n' "$OWNER" "$JAR_PATH"
docker network create --label "$OWNERSHIP_LABEL" "$NETWORK" >/dev/null
network_created=true
owned_run "$POSTGRES_CONTAINER" --network-alias postgres \
  -e POSTGRES_PASSWORD=fixture-only -e "POSTGRES_DB=$TEST_DATABASE" postgres:16-alpine
owned_run "$KAFKA_CONTAINER" --network-alias kafka \
  -e KAFKA_NODE_ID=1 -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:9093 \
  -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092 \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  -e KAFKA_AUTO_CREATE_TOPICS_ENABLE=true \
  -e CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qk confluentinc/cp-kafka:7.4.0
wait_for_dependency "$POSTGRES_CONTAINER" pg_isready -U postgres
wait_for_dependency "$KAFKA_CONTAINER" kafka-broker-api-versions --bootstrap-server kafka:9092
bounded_command "$TIMEOUT_SECONDS" docker exec "$KAFKA_CONTAINER" kafka-topics --bootstrap-server kafka:9092 \
  --create --topic "$TEST_TOPIC" --partitions 1 --replication-factor 1 >/dev/null
start_application "$CRASH_CONTAINER" -p 127.0.0.1::5005 \
  -e 'JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=0.0.0.0:5005'
readonly DEBUG_PORT="$(docker port "$CRASH_CONTAINER" 5005/tcp | awk -F: '{print $NF}')"
wait_for_container_log "$CRASH_CONTAINER" 'Started SettlementServiceApplication'
wait_for_group_assignment

docker exec -i "$POSTGRES_CONTAINER" psql -U postgres -d "$TEST_DATABASE" \
  -v shipper_id="$SHIPPER_ID" -v deposit_amount=120000 \
  < scripts/seed-settlement.sql >/dev/null

"$JAVA_BIN" --add-modules jdk.jdi scripts/JdwpBreakpointProbe.java \
  127.0.0.1 "$DEBUG_PORT" "$PROBE_CLASS" "$PROBE_METHOD" \
  </dev/null >"$probe_log" 2>&1 &
probe_pid=$!
wait_for_probe_log 'BREAKPOINT_'

payload="{\"eventId\":\"$EVENT_ID\",\"eventType\":\"DELIVERY_COMPLETED\",\"deliveryId\":$DELIVERY_ID,\"orderId\":$ORDER_ID,\"restaurantId\":$RESTAURANT_ID,\"shipperId\":$SHIPPER_ID,\"totalPrice\":120000,\"restaurantEarnings\":80000,\"restaurantCommission\":20000,\"shippingFee\":20000,\"shipperEarnings\":17000,\"shippingCommission\":3000,\"totalPlatformEarnings\":23000,\"paymentMethod\":\"COD\"}"
printf '%s:%s\n' "$DELIVERY_ID" "$payload" \
  | bounded_command "$TIMEOUT_SECONDS" docker exec -i "$KAFKA_CONTAINER" kafka-console-producer \
      --bootstrap-server kafka:9092 --topic "$TEST_TOPIC" \
      --property parse.key=true --property key.separator=: >/dev/null

wait_for_probe_log 'BREAKPOINT_REACHED'
database_invariants_hold || {
  printf '%s\n' 'Database was not durably committed at the afterCommit breakpoint.' >&2
  exit 1
}

committed_ledger="$(ledger_snapshot)"
deadline=$((SECONDS + TIMEOUT_SECONDS))
current_offset='' log_end_offset=''
while (( SECONDS < deadline )); do
  remaining=$((deadline - SECONDS))
  (( remaining <= 10 )) || remaining=10
  log_end_offset="$(group_field 5 "$remaining")"
  (( SECONDS < deadline )) || break
  remaining=$((deadline - SECONDS))
  (( remaining <= 10 )) || remaining=10
  current_offset="$(group_field 4 "$remaining")"
  [[ "$log_end_offset" == '1' && -n "$current_offset" ]] && break
  sleep 1
done
[[ "$log_end_offset" == '1' && ( "$current_offset" == '-' || "$current_offset" == '0' ) ]] || {
  printf 'Missing/unexpected offset before ACK: current=%s end=%s\n' \
    "${current_offset:-<none>}" "${log_end_offset:-<none>}" >&2
  exit 1
}

printf '%s\n' '[SETTLEMENT-CRASH] SIGKILL after DB commit and before ACK'
docker kill --signal=KILL "$CRASH_CONTAINER" >/dev/null
kill "$probe_pid" >/dev/null 2>&1 || true
probe_pid=''
docker rm "$CRASH_CONTAINER" >/dev/null

start_application "$RECOVERY_CONTAINER"
wait_for_container_log "$RECOVERY_CONTAINER" \
  "[Idempotent] Settlement event $EVENT_ID already applied, skipping"

deadline=$((SECONDS + TIMEOUT_SECONDS))
while (( SECONDS < deadline )); do
  remaining=$((deadline - SECONDS))
  (( remaining <= 10 )) || remaining=10
  current_offset="$(group_field 4 "$remaining")"
  (( SECONDS < deadline )) || break
  remaining=$((deadline - SECONDS))
  (( remaining <= 10 )) || remaining=10
  log_end_offset="$(group_field 5 "$remaining")"
  (( SECONDS < deadline )) || break
  remaining=$((deadline - SECONDS))
  (( remaining <= 10 )) || remaining=10
  lag="$(group_field 6 "$remaining")"
  if [[ "$current_offset" == '1' && "$log_end_offset" == '1' && "$lag" == '0' ]]; then
    break
  fi
  sleep 1
done
[[ "$current_offset" == '1' && "$log_end_offset" == '1' && "$lag" == '0' ]]
database_invariants_hold
[[ "$(ledger_snapshot)" == "$committed_ledger" ]] || {
  printf '%s\n' 'Receipt, ledger or balance rows changed on Kafka redelivery.' >&2
  exit 1
}

printf '%s\n' \
  'Settlement crash-window proof passed: durable commit, uncommitted offset, SIGKILL, exact redelivery, unchanged ledger, lag 0.'
