# Hot Query And Index Audit

Date: 2026-07-30

## Method

`scripts/verify-hot-query-plans.sh` creates a disposable PostgreSQL 16 database
with 100k–250k representative rows per hot table, installs the pre-Phase-4
indexes, captures `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`, applies only the
candidate indexes, and captures the same plans again. JSON plans and a timing
summary are written to `target/phase4-query-plans/`.

The fixture covers customer/restaurant/shipper/status/global order timelines,
delivery offer/history, settlement ledger/pending withdrawals, notification
inbox, shipper availability and both pending outboxes. Timing is a simple local
benchmark, not a production capacity promise; node/index choice and eliminated
scan/sort work are the primary evidence.

## Findings

| Path | Baseline | Decision/evidence |
|---|---|---|
| Orders by customer, restaurant, shipper, status or owner | Existing composite index scans; bounded page size 1–100 | Keep existing indexes; adding DESC duplicates is unnecessary because PostgreSQL can backward-scan B-tree indexes. |
| Global order timeline | Parallel sequential scan plus top-N sort; 23.072 ms for 150k rows | Add `(created_at DESC, id DESC)`; plan becomes ordered index scan, 0.069 ms. |
| Order item mapping | One lazy collection query per order (N+1) | `@BatchSize(100)` reduces a ten-order page to page + count + one item batch query; executable statistics test caps it at three statements. |
| Delivery history/current offer | Existing `shipper_id,created_at` and `offered_shipper_id,offer_expires_at` index scans; 100/2-row limits | Keep. The one-active-delivery partial unique index also bounds active assignment contention. |
| Settlement entity ledger | Existing prefix index plus an in-memory sort over a small bounded entity set; 0.560 ms | Keep existing index. A larger ordered entity index was tested, but PostgreSQL still preferred the narrower existing index, so it was removed from the change. |
| Pending withdrawals | Status index, reason filter and top-N sort; 5.354 ms | Filter `status + reason` in the repository before limiting and use `(status,reason,created_at DESC,id DESC)`; 0.219 ms. This also fixes missing withdrawals when other pending reasons fill the first 100 rows. |
| Global settlement timeline | Existing `created_at` ordered scan; bounded compatibility list 100 | Keep. Candidate replacement did not improve the plan and was removed. |
| Notification inbox | Existing `(user_id,is_read,created_at)` index scan; service cap 100 | Keep. Mark-all is intentionally a bounded-user write set, not a list query. |
| Match/shipper availability | Match uses bounded Redis GEO radius/offer operations; shipper DB compatibility list uses `is_online` index and cap 100 | Keep; no relational write or unbounded query was added to the match/location hot path. |
| Pending outboxes | Index-supported bounded `SKIP LOCKED` batch, but the old query ordered by creation while retry eligibility used `next_attempt_at` | Align query and index to `status,next_attempt_at,created_at,id`; batch is clamped to 1–500 and peers avoid row contention with `SKIP LOCKED`. |

Source scan found no parameterless production `findAll()` call. The remaining
parameterless calls are test fixtures. Production compatibility list paths are
bounded: Order accepts page sizes 1–100, Delivery/Notification/Shipper cap at
100, Settlement caps at 100, and outbox relays clamp batches at 500.

## Added migrations

- Order V7: global order timeline and corrected pending-outbox index.
- Delivery V13: corrected pending-outbox index.
- Settlement V3: pending-withdrawal composite index, replacing the now-redundant
  status-only index.

Each migration is exercised from a clean schema and applicable legacy baseline
by the module Flyway tests. The PostgreSQL benchmark builds all candidate
indexes in one second on its representative local dataset.

## Lock and write-path impact

These Flyway migrations use transactional `CREATE INDEX`, which takes a
PostgreSQL `SHARE` lock and can briefly block concurrent `INSERT`, `UPDATE`, or
`DELETE` on the indexed table. Deploy during a low-write window after setting a
short session `lock_timeout` and a bounded `statement_timeout`; the benchmark
fixture demonstrates the intended preflight values (5 seconds / 10 minutes).
If lock acquisition times out, leave the old application version running and
retry the migration later—do not disable the timeout or kill business writers.

Order creation gains one global timeline index. Both outbox paths replace an
existing index rather than add a duplicate. Settlement replaces the status-only
index with one composite used by the corrected pending-withdrawal path. No new
index is added to location, match, ledger insert business-key, or delivery
assignment write-critical columns beyond these evidenced replacements.
