# Simulator scenarios Phase 8–9

`Phase8ScenarioCatalog.defaults()` is the deterministic regression catalog. Each
scenario has a stable seed so a failed run can be reproduced and compared across
builds. The catalog is intentionally production-isolated; simulator traffic must
use the simulator namespace, actor pool, and non-production Kafka topics.

Catalog entries describe duplicate Kafka delivery, consumer restart, search replay, voucher
contention, flash-sale stock contention, livestream checkout retry, and soft-delete
recovery. Record the run id, seed, correlation id, decision trace, and recovery
result in the simulator journal. Never point a simulator run at production URLs,
databases, caches, or Kafka topics.

The catalog is metadata only: these entries are not yet connected to executable
failure injection in `SimulationService`. A passing catalog test is not a
completed recovery drill.

Lease safety: an active run is aborted when renewal returns false or throws a
runtime exception. On exception the in-memory abort happens before the original
failure is propagated; no exception details are added to the public timeline.
Terminal and orphaned memory runs do not renew leases. Unit tests exercise these
paths without Gateway traffic; they do not establish database fencing under
concurrent workers or interrupt business requests already in flight.

Cleanup safety: a terminal run retains its memory lease list when
`releaseOrQuarantine` throws, allowing retry with the original lease ID and
fencing token. The list is removed only after all calls complete; a false
return remains a completed conservative quarantine outcome, not an exception.
If part of a multi-actor cleanup completed before failure, retry can repeat
those calls and conservatively quarantine previously released actors. Run TTL
aborts execution but does not itself release actor fences.

Scenario persistence and public snapshots share the journal's recursive
redaction of `token`, `accessToken` and `ownerToken`. Runtime scenario credentials
remain in memory for actor actions; redaction operates on a copy. This applies
to future writes only. Existing durable rows, free-text secrets and other
credential key names require a separate audit/remediation scope.

Journal reads also redact those known keys on a copy, including legacy rows,
without rewriting stored data. Malformed JSON produces a generic
`journalError: invalid payload` for that row; later valid entries remain
available and parser details are not exposed.
