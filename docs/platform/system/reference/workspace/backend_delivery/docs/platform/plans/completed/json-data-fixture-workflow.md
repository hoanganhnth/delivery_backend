# Execution Plan: Data folder và JSON fixture workflow

Date: 2026-08-21

## Status

Completed

## Outcome

Có một thư mục `data/` dùng chung cho JSON payload/scenario của hệ thống. Một
runner có thể nhận scenario JSON, gọi API theo thứ tự, thay biến từ env và
capture ID từ response. Restaurant web form và menu web form có thể paste hoặc
chọn cùng JSON đó để tự điền dữ liệu trước khi submit.

## Context

- Quy trình: [docs/WORKFLOW.md](../../WORKFLOW.md) và [AGENTS.md](../../../../../AGENTS.md).
- Catalog authority: `backend_delivery/restaurant-service`.
- Existing catalog fixture: `backend_delivery/scripts/fixtures/realistic-catalog.json`.
- `delivery_web` đang có nhiều thay đổi chưa commit; chỉ chạm form/importer
  files liên quan trực tiếp, không sửa hay hoàn tác phần khác.

## Scope

In scope:

- Shared `data/` folder with catalog, API request templates, UI payloads and
  HTTP/catalog scenario manifests.
- Node runner with dry-run default, env/step interpolation, response captures
  and no token persistence.
- JSON import/prefill for restaurant and menu forms.
- Focused web tests and static/runtime checks that do not require Docker.

Out of scope:

- Generic autofill for Flutter/React Native screens or every admin form.
- Direct database writes, token/password files, private platform crawling, or
  enabling disabled voucher/flash-sale checkout.

## Recovery

- Runner defaults to dry-run; use `RUN_FIXTURE=true` explicitly for API writes.
- API fixture reruns may create duplicates because no fixture namespace/upsert
  contract exists; use a disposable local environment or delete the created
  rows through normal ownership paths.
- UI import changes only local form state until the user submits.

## Progress

- [x] Define shared data/runner/UI boundary while preserving dirty worktree.
- [x] Move catalog source of truth into `data/` and add templates/scenarios.
- [x] Add and validate JSON API runner.
- [x] Add and test web form JSON prefill.
- [x] Record validation and limitations; complete the plan.

## Validation

- JSON parse and schema/invariant checks.
- Node dry-run and focused Web tests/typecheck/build passed.
- No live API write unless explicitly invoked with `RUN_FIXTURE=true` and token.

## Result

Implemented `data/` as the shared JSON source, added the dry-run API/catalog
runner, and added paste/file import to the restaurant and menu forms. Validation:
12 JSON files parse, runner dry-runs catalog and chained restaurant/menu requests,
focused Web tests pass 9/9, typecheck/lint/build pass. Live API ingestion remains
unattempted because no Gateway/token was supplied.
