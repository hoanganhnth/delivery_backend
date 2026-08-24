# 0005 Matching Algorithm Decision Trace

Date: 2026-08-21

## Status

Accepted for the dev/test Scenario Lab vertical slice.

## Context

Matching correctness currently lives across Match's Redis GEO projection,
Settlement COD eligibility and the durable result outbox. A browser cannot
reliably explain the decision by reimplementing that logic from source code or
from the simulator's configured candidate oracle. At the same time, replacing
the active matcher before a safe comparison path exists would risk real offer
assignment.

## Decision

- `nearest-cod-v1` remains the sole authoritative active algorithm.
- Match emits a versioned, read-only `matching.decision-trace` event only after
  `shipper.found` or `shipper.not-found` is durable. The event contains the
  observed stages, latency, attempt count, candidate rank/distance, COD result,
  rejection reason and selected shipper.
- The event is relayed through Match's outbox with the order ID as Kafka key.
  It is not an input to reservation, assignment, Saga convergence or retry
  decisions. A trace failure may reduce observability but cannot change the
  business result.
- `simulator-service` consumes the source with its own group and exposes the
  traces through run snapshots/SSE and `GET /algorithm-traces`. It correlates
  using both order and delivery identity, with a bounded short-lived buffer for
  Kafka-before-poll ordering. The UI labels the candidate list as post-GEO
  filter rather than a raw Redis pool.
- New algorithms must first run as read-only `SHADOW` evaluations against the
  same decision context. No shadow algorithm may reserve or send an offer until
  a separately approved rollout policy defines comparison metrics, data
  completeness, rollback and ownership of the active result.

## Consequences

The Scenario Lab can show the real active decision and compare measured stage
latency without changing fulfilment behavior. Trace retention is currently the
in-memory run lifetime, so durable historical analytics and DB/ledger observer
remain follow-up work. A future shadow registry must preserve the same read-only
boundary and must make incomplete candidate facts visible instead of guessing.
