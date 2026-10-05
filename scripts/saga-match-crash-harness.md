# Saga/Match crash-replay rehearsal

`verify-saga-match-crash-replay.sh` runs the existing two-replica proof using
fresh packages from `dispatch/boot` (artifact `saga-orchestrator-service`) and
`match/boot` (artifact `match-service`). It does not build Maven packages itself.
Package selection uses the current POM's exact artifact/version, never a glob
or legacy-layout fallback. Freshness includes root/parent POMs and transitive
local Maven runtime dependencies, including shared starters; test/provided
dependencies are excluded.

## Prerequisites and commands

Run from the repository root with JDK 17, Maven, Python 3, Bash, curl, jq,
uuidgen, OpenSSL, and a running Docker daemon. Docker Compose v2 must support
`config --format json` and `build.dockerfile_inline`. Allow image downloads and
enough Docker memory for the core stack and two Match replicas. Existing local
operator-owned secrets must be configured through `.env` or exported file-path
variables (`JWT_PRIVATE_KEY_FILE`, `JWT_PUBLIC_KEY_FILE`, `INTERNAL_SECRET_FILE`,
`DB_PASSWORD_FILE`, `WEB_BFF_ENCRYPTION_KEY_FILE`) plus `GRAFANA_ADMIN_PASSWORD`.
If these have not been provisioned, run `bash scripts/gen-keys.sh` first; it
preserves valid existing JWT keys unless rotation is explicitly requested.

```bash
python3 scripts/test-saga-match-crash-harness.py
bash -n scripts/verify-saga-match-crash-replay.sh
mvn -q -DskipTests clean package
SAGA_MATCH_CRASH_RUN_ID="rehearsal-$(date +%Y%m%d%H%M%S)" \
SAGA_MATCH_CRASH_TIMEOUT_SECONDS=600 \
  bash scripts/verify-saga-match-crash-replay.sh
```

The timeout defaults to 240 seconds and accepts positive integers up to 86400.
It bounds each build/start/seed operation and each observation window; it is
not a whole-rehearsal deadline. Polling Docker/curl commands are additionally
bounded by the remaining observation budget. Run IDs accept 1–40 alphanumeric
or hyphen characters, beginning with an alphanumeric character. No positional
arguments are supported.

Expected final line (identities vary):

```text
Saga/Match two-replica crash/replay rehearsal passed: order=<id> delivery=<id> command=<uuid>, one Match result/outbox, one Delivery offer, one notification and one Saga cache command.
```

## Preserved assertions

- Two Match replicas run with Find paused and Stop active; Saga emits exactly
  one SENT Find for the cancelled order, carrying numeric delivery identity
  and UUID matching-session identity, while Match has zero commands.
- A global Redis outage causes Gateway cancellation HTTP 503 and leaves the
  order's status/reason/canceller/cancellation-outbox snapshot unchanged.
- With Redis unavailable only to Match, Saga emits exactly one SENT Stop;
  exactly one durable generation tombstone exists with PENDING projection and
  at least one projection attempt; Stop's source offset commits and its DLT
  stays empty.
- Restoring Redis yields exactly one PROJECTED tombstone with a projection
  timestamp and the corresponding Redis cancellation key.
- Resuming delayed Find yields exactly `1|CANCELLED|0`: one cancelled command,
  zero Match result outboxes, no current offer for that order, zero MATCH_FOUND
  notifications and zero Saga cache-shipper-found commands.
- A separate confirmed COD order stages exactly `1|RESULT_STAGED|1|PENDING`,
  with complete durable command UUID, numeric delivery ID and stored payload.
- SIGKILL of Match followed by two recovered replicas with relay enabled
  restores the same order/delivery's WAIT_SHIPPER_CONFIRM offer and marks the
  command's outbox SENT.
- Replaying the original stored Find preserves exactly
  `1|RESULT_STAGED|1|SENT`, one Delivery row in WAIT_SHIPPER_CONFIRM, one
  MATCH_FOUND notification and one Saga cache-shipper-found command. The
  harness also waits for replay source offsets and durable effects to converge.

## Isolation and cleanup

Canonical Compose files are read only to resolve configuration. The generated
temporary configuration removes fixed container identities and host ports
(except a random loopback Gateway port), uses fresh owner-named volumes and
network, and gives every container and fixture-built image the unique
`delivery.saga-match-crash.owner` label. PostgreSQL, Kafka and Redis belong only
to this disposable fixture. Match settings are updated in that configuration
for each recreation, including the actual Spring Redis host override.

EXIT, INT and TERM cleanup enumerates only owner-label-filtered resources,
rechecks their labels, removes containers (including stopped ones), volumes,
network and fixture images, and preserves the exit status. No canonical
Compose stack is started, stopped, reconciled or torn down; operator secrets
are never deleted. Failed runs capture owned container logs before removal.
As with other trap-based harnesses, SIGKILL of the harness itself or an
unresponsive Docker daemon can prevent cleanup. The real Docker rehearsal is
separate from the fast tests and must be run by the parent agent.
