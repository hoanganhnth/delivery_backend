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

The fixture enables Shipper's identity mapping relay and enforces canonical
shipper projections in Tracking and Delivery, following the domain-ID wave in
`docs/runbooks/identity-principal-event-rollout.md`. Seed waits for both real
Kafka projections before publishing locations. JWT user/principal IDs are not
shipper aggregate IDs. Business-result outbox assertions exclude the separate
`matching.decision-trace` telemetry topic and require a `shipper.found` result;
duplicate business results and `shipper.not-found` still fail the staging check.

EXIT, INT and TERM cleanup enumerates only owner-label-filtered resources,
rechecks their labels, removes containers (including stopped ones), volumes,
network and fixture images, and preserves the exit status. No canonical
Compose stack is started, stopped, reconciled or torn down; operator secrets
are never deleted. Failed runs capture owned container logs before removal.
If the COD Match staging wait expires, the harness first dumps the order's
complete Match command and outbox rows (including decision-trace payloads),
Settlement COD holds and all fixture shipper balances, Redis reservation and
freshness keys with values/TTLs, and Match/Settlement logs to stderr. Each
diagnostic command has a separate ten-second budget after the observation
deadline; diagnostic failures preserve the failed wait and cleanup outcome.
As with other trap-based harnesses, SIGKILL of the harness itself or an
unresponsive Docker daemon can prevent cleanup. The real Docker rehearsal is
separate from the fast tests.

## COD failure diagnosis and packaged proof (2026-10-06)

The failure after the GEO hit was a fixture identity mismatch. The diagnostic
rehearsal in `/tmp/saga-match-diagnostic-rehearsal.log` recorded GEO candidate
`shipperId=4`, `codEligible=false`, reason `COD_NOT_ELIGIBLE`, and reservation
stage `NOT_RUN`. Settlement had only shipper `entity_id=1`, deposit `500000.00`,
reserved deposit `0.00`; the Find amount was `57000.00`. No COD holds existed.
This explains why the earlier successful eligibility probe for shipper 1 did
not validate the actual GEO candidate. Duplicate consumption by two replicas
was not the rejection cause: eligibility filtered the candidate before staging
or reservation. Single-offer matching does not call the batch COD hold API.

Tracking's identity projection defaults to unenforced, allowing the legacy
user ID fallback when its mapping is absent. The isolated fixture now enables
the real Shipper identity outbox relay, enforces canonical projections in
Tracking and Delivery, and seed waits for both mappings before posting a
location. This follows `docs/runbooks/identity-principal-event-rollout.md`;
no synthetic mapping, GEO entry, financial balance for a legacy ID, or service
code change is needed.

Validation performed by this worker:

- `mvn -q -DskipTests clean package`: exit 0.
- `python3 scripts/test-saga-match-crash-harness.py`: 24 tests passed.
- `bash -n scripts/verify-saga-match-crash-replay.sh`: exit 0.
- `SAGA_MATCH_CRASH_RUN_ID=rehearsal-1791296486 SAGA_MATCH_CRASH_TIMEOUT_SECONDS=900 bash scripts/verify-saga-match-crash-replay.sh`:
  exit 0; passed all crash/replay assertions for order/delivery 2, command
  `ee478d9b-17f8-4602-82ad-b760eacc3748`. Log:
  `/tmp/saga-match-rehearsal-current.log`.

The passing staging trace selects canonical shipper 1, reports COD eligibility
`PASSED` and reservation `WON`, and persists one pending `shipper.found` result.
Final assertions prove exactly one durable offer, notification, and Saga cache
command after SIGKILL, recovery, and replay. Cleanup left no owner-labelled
containers, volumes, networks, or images. Sanitized database/trace evidence
is retained in `scripts/saga-match-crash-evidence.json`. The obsolete Order
envelope defect report and requested temporary failure log were removed;
no production defect is required to resolve this COD failure. Assertions and
the requested 900-second observation timeout were preserved.
