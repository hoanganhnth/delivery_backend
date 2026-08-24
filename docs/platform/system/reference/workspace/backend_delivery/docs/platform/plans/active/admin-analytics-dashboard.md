# Execution Plan: Full Platform Analytics Dashboard

Date: 2026-08-22

## Status

Active

## Outcome

Expose a trustworthy analytics dashboard for `ADMIN` and `SHOP_OWNER` through
the Gateway, with GMV and settlement-backed gross/net platform commission,
full-history backfill, near-real-time projection, bounded CSV export, and
scope-safe Web UI.

## Context

- `delivery_web/src/modules/admin/pages/AdminDashboard.tsx` is currently a
  shortcut-only page.
- `analytics-service` is default-off and not routed by the Gateway.
- Analytics currently consumes a non-canonical order status topic and does not
  enforce actor ownership in its controller.
- The system-level product contract requires Gateway-only browser access,
  server-side authorization, `Asia/Ho_Chi_Minh` reporting, event-date trends,
  30-day cohort closure, 366-day dashboard range, 5-year aggregate CSV, and no
  PII in exports.

## Scope

In scope:

- Canonical analytics read API and authorization for admin/owner scopes.
- Additive analytics projection/read model and source-backed reconciliation.
- Historical backfill job with deterministic idempotency and checkpointing.
- Web admin and restaurant analytics dashboards with polling, freshness state,
  comparison, and aggregate CSV export.
- Focused contract, security, persistence, and UI proof.

Out of scope:

- Per-item analytics until an order-item source of truth exists.
- Online-payment reporting before provider/reconciliation is enabled.
- Browser access to reconciliation/operator commands.
- PII/order-level CSV export.

## Approach

1. Freeze event/metric semantics and add the plan-owned contract types.
2. Add canonical order and settlement analytics events through transactional
   outbox boundaries; preserve existing core flow behavior.
3. Build analytics raw receipt, order fact, financial fact, daily aggregate,
   freshness, and backfill checkpoint projections with atomic deduplication.
4. Implement operator-only full-history snapshot backfill, Kafka-delta cutover,
   reconciliation, and quarantine for unmappable legacy records.
5. Enforce `ADMIN` and restaurant ownership at service and Gateway boundaries;
   route only bounded read/export endpoints.
6. Add Web adapters, typed parsers, scope-aware dashboards, SVG charts,
   polling, stale/degraded states, and server-side CSV download.
7. Validate with focused unit/context tests, PostgreSQL/Kafka rehearsal,
   Gateway security tests, Web `npm run verify`, and seeded browser smoke.

## Risks And Recovery

- Existing worktree changes are user-owned; inspect overlapping diffs before
  editing and keep changes additive.
- Historical source rows may lack canonical timestamps or settlement linkage;
  quarantine them and expose coverage instead of guessing.
- Analytics rollout can be disabled by feature flag and Gateway route removal;
  retain raw/projection tables for replay and do not truncate data.
- Financial drift blocks `FRESH` status and export until reconciliation passes.

## Progress

- [x] Admin read contract, backend authorization, and Gateway route implemented
      as the first vertical slice.
- [ ] Canonical events and settlement-backed projections implemented.
- [ ] Full-history backfill and reconciliation implemented.
- [x] Web admin dashboard, typed adapter, polling, SVG trend, KPI/status/top
      restaurant views implemented.
- [ ] Owner dashboard and aggregate CSV export implemented.
- [x] Focused compile, lint, typecheck, UI, Gateway, and contract validation
      passed for the vertical slice.

## Decisions

- 2026-08-22: Report in `Asia/Ho_Chi_Minh`; range is `[from,to)`.
- 2026-08-22: Trend uses event dates; completion/cancellation uses a 30-day
  closed cohort.
- 2026-08-22: GMV is delivered-order `totalPrice`; platform commission is
  settlement-ledger-backed with separate gross and net values.
- 2026-08-22: Dashboard polls every 60 seconds; CSV is aggregate-only and
  bounded to five years.
- 2026-08-22: Backfill uses source DB snapshots plus idempotent replay and
  Kafka delta after a recorded watermark.

## Validation

- Focused proof: analytics metric, projection, auth, parser, CSV, and UI tests.
- Integration proof: PostgreSQL migration, duplicate/replay, backfill resume,
  source-vs-projection and ledger-vs-projection reconciliation.
- Repository-required checks: backend module tests/compile; `delivery_web`
  `npm run verify`; Gateway route/security tests.

## Result

First vertical slice is implemented. The analytics service remains
`ANALYTICS_PROCESSING_ENABLED=false` by default, so production rollout is not
claimed complete. Full-history backfill, canonical event migration,
settlement gross/net projection, owner isolation, export, and runtime
reconciliation remain required before enabling the capability broadly.
