# Execution Plan: Gói mock data catalog thực tế cho local development

Date: 2026-08-21

## Status

Completed

## Outcome

Có một data pack nhà hàng/món ăn TP.HCM có provenance từ các trang công khai
ShopeeFood và GrabFood, được chuẩn hoá theo contract hiện tại của
`restaurant-service`, cùng script seed qua API Gateway để có thể nạp lại vào
local backend mà không ghi thẳng vào database.

## Context

- Quy trình: [docs/WORKFLOW.md](../../WORKFLOW.md) và [AGENTS.md](../../../../../AGENTS.md).
- Catalog authority: `backend_delivery/restaurant-service` và
  [restaurant & menu spec](../../../services/restaurant_and_menu.md).
- Data ownership: [events-and-data](../../system/events-and-data.md).
- Existing minimal runtime fixture: `backend_delivery/scripts/seed.sh`.
- Public source snapshots are reference data only; account, address khách,
  shipper, order, delivery, ledger and event rows remain synthetic.

## Scope

In scope:

- 10 snapshot restaurant records around TP.HCM with source URL, observed date,
  source facts and clearly labelled approximate coordinates.
- 4 menu items per restaurant using the current `menu_item` write contract.
- Dry-run/real API seed script for restaurant and menu item creation.
- Documentation of fields that are source-backed, normalized or synthetic.

Out of scope:

- Crawling private/API endpoints, bypassing GrabFood 403/anti-bot controls, or
  high-volume collection.
- Personal data, customer accounts, driver identity documents, real phone
  numbers, reviews, or payment credentials.
- Direct SQL writes, seeded orders/deliveries/ledger, search index writes,
  voucher/flash-sale activation, or schema changes for categories/toppings.

## Approach

1. Capture only low-volume public listing/detail facts from official pages and
   retain source URLs and `observedAt` in the fixture.
2. Keep the canonical seed payload limited to fields accepted by the current
   restaurant/menu APIs; store rating/category/ETA/price range as metadata.
3. Create restaurants and menu items through Gateway using a caller-provided
   `SHOP_OWNER` token; allow a dry-run that validates JSON without mutations.
4. Validate JSON, shell syntax, source provenance, and repository diff hygiene.

## Risks And Recovery

- Restaurant availability, address, price and promotion facts change. The
  snapshot is dated and must be refreshed rather than treated as production
  truth.
- Approximate coordinates may be unsuitable for real dispatch. They are marked
  as approximate and must not be used as operational geocoding evidence.
- Re-running the API seed creates duplicates because the current API has no
  fixture namespace/upsert contract. Recovery is to delete the local fixture
  restaurants through the owner/admin path or reset the disposable local DB;
  the script does not silently mutate existing rows.

## Progress

- [x] Inspect repository contracts and current seed behavior.
- [x] Define source/synthetic boundaries and catalog scope.
- [x] Add the normalized fixture and provenance.
- [x] Add and validate the Gateway seed script.
- [x] Record validation and limitations; move the plan when complete.

## Decisions

- 2026-08-21: Use TP.HCM as the initial geography because the existing local
  seed and simulator use TP.HCM coordinates and order examples.
- 2026-08-21: Do not add categories, toppings, live ratings or order history to
  the backend write path because the current canonical schema does not own
  those fields in the restaurant/menu create contracts.
- 2026-08-21: Do not bypass GrabFood access controls; use publicly indexed
  official category/chain pages as a low-volume reference where direct fetch is
  restricted.

## Validation

- Focused proof: `jq empty` / structural checks for the data pack and
  `bash -n` for the seed script.
- Integration or end-to-end proof: not run; a local Gateway and authenticated
  `SHOP_OWNER` fixture were not supplied. The script remains dry-run by default.
- Repository-required checks: targeted new-file whitespace check, catalog
  invariant check, dry-run execution, and backend worktree inspection. `shellcheck`
  was unavailable in the environment.

## Result

Implemented and statically validated. Added 10 restaurants, 40 menu items, source
provenance, and a safe Gateway seed script. Runtime API ingestion, Elasticsearch
projection and full order-flow fixtures remain follow-up work because they need a
local runtime and authenticated fixture identities.
