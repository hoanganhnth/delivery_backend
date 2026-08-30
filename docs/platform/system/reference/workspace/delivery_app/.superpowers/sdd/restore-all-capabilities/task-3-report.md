# Task 3 Report — Flash Sale and promotion restoration

Date: 2026-08-27

## Outcome

Completed the customer-app Flash Sale restoration slice without changing the
backend simulation or the pre-existing auth work. The Flash Sale catalog now
uses the complete backend campaign schema in its contract fixtures, retains a
typed `FormatException` at the compatibility catalog boundary, surfaces
loading and retryable error states, and reads campaigns/items through domain
use cases. The existing catalog-to-cart, checkout preview, and order creation
paths continue to carry the server-issued `flashSaleItemId`.

The backend public contract was traced in
`backend_delivery/docs/platform/system/api/http-contract-catalog.md` and in
`flashsale-service` source. `FlashSaleCampaignDto` exposes `id`, `name`,
`isRecurring`, `startTime`, `endTime`, and `status`; public items expose
`originalPrice` as well as the sale price and inventory fields. No backend
change was needed: `FLASHSALE_CHECKOUT_ENABLED` remains false by default in
both the Flash Sale service and the Flutter runtime configuration.

## Files changed

- `lib/features/flash_sale/domain/usecases/flash_sale_usecases.dart` — added
  campaign and campaign-item use cases, including invalid-ID rejection.
- `lib/features/flash_sale/di/flash_sale_providers.dart` — added use-case
  providers and a test-overridable checkout gate that defaults to the existing
  runtime flag.
- `lib/features/flash_sale/application/flash_sale_view_model.dart` — uses the
  domain use cases and exposes campaign-load failures instead of treating them
  as an empty catalog.
- `lib/features/flash_sale/presentation/views/flash_sale_banner_view.dart` —
  added loading and retryable error presentation; empty inventory remains
  hidden.
- `lib/features/flash_sale/data/repositories/flash_sale_repository_impl.dart`
  — preserves `FormatException` for compatibility callers when a catalog
  validation failure is returned.
- `test/features/cart/presentation/providers/checkout_capability_contract_test.dart`
  — corrected public campaign and item fixtures to the backend DTO schema.
- `test/features/flash_sale/application/flash_sale_view_model_test.dart` —
  covers retryable campaign-load failure.
- `test/features/flash_sale/data/repositories/flash_sale_repository_impl_test.dart`
  — covers server-authoritative Flash Sale identity preservation.
- `test/features/flash_sale/domain/usecases/flash_sale_usecases_test.dart` —
  covers repository delegation and local campaign-ID validation.
- `test/features/flash_sale/presentation/views/flash_sale_banner_view_test.dart`
  — covers loading, retryable error, populated, and empty states.

## Root cause

The two original checkout contract failures had separate causes:

1. The test fixtures supplied only campaign IDs/names and omitted backend
   required parser fields: `isRecurring`, `startTime`, `endTime`, and `status`.
   They also omitted each item's `originalPrice`, which the client correctly
   validates so it can prove the flash price is a discount.
2. `FlashSaleCatalogClient` folds repository failures into a generic
   `Exception`; this erased the compatibility caller's required
   `FormatException` for malformed or conflicting catalog data.

## Validation

Initial reproduction:

```text
fvm flutter test test/features/flash_sale test/features/catalog test/features/cart
Result: 2 failures in checkout_capability_contract_test.dart.
```

Focused red/green proof:

```text
fvm flutter test test/features/flash_sale test/features/cart/presentation/providers/checkout_capability_contract_test.dart
Result: All tests passed (12 tests).
```

Requested focused suite:

```text
fvm flutter test test/features/flash_sale test/features/catalog test/features/cart
Result: All tests passed (50 tests).
```

Static analysis and diff hygiene:

```text
fvm flutter analyze lib/features/flash_sale test/features/flash_sale test/features/cart/presentation/providers/checkout_capability_contract_test.dart
Result: No issues found.

git diff --check
Result: exit 0, no whitespace errors.
```

## Remaining concerns

- Flash Sale reservation remains intentionally default-off. No sandbox
  reservation/commit/release proof was run or claimed in this task.
- The public catalog contract uses recurring `LocalTime` windows; the app
  derives the local countdown end instant. Time-zone and midnight behavior
  should be exercised in a sandbox before enabling checkout outside local
  development.
- The app workspace still contains unrelated pre-existing auth and catalog
  changes; backend simulation work is also untouched.

## Commit

Committed focused Task 3 changes as:

```text
353733d18d995ba81b648f6f207d370e10617037 feat(app): restore flash sale customer flow
```

Exact staged file list:

```text
lib/features/catalog/application/catalog_restaurant_detail_view_model.dart
lib/features/catalog/presentation/pages/catalog_home_page.dart
lib/features/catalog/presentation/views/catalog_home_view.dart
lib/features/flash_sale/application/flash_sale_state.dart
lib/features/flash_sale/application/flash_sale_view_model.dart
lib/features/flash_sale/data/datasources/flash_sale_remote_data_source.dart
lib/features/flash_sale/data/datasources/flash_sale_remote_data_source_impl.dart
lib/features/flash_sale/data/models/flash_sale_campaign_model.dart
lib/features/flash_sale/data/models/flash_sale_item_model.dart
lib/features/flash_sale/data/repositories/flash_sale_repository_impl.dart
lib/features/flash_sale/di/flash_sale_providers.dart
lib/features/flash_sale/domain/entities/flash_sale_campaign_entity.dart
lib/features/flash_sale/domain/entities/flash_sale_item_entity.dart
lib/features/flash_sale/domain/repositories/flash_sale_repository.dart
lib/features/flash_sale/domain/usecases/flash_sale_usecases.dart
lib/features/flash_sale/flash_sale.dart
lib/features/flash_sale/presentation/pages/flash_sale_banner_page.dart
lib/features/flash_sale/presentation/views/flash_sale_banner_view.dart
lib/features/flash_sale/presentation/widgets/countdown_timer.dart
lib/features/flash_sale/presentation/widgets/flash_sale_item_card.dart
lib/features/restaurants/di/flash_sale_catalog_provider.dart
test/features/cart/presentation/providers/checkout_capability_contract_test.dart
test/features/flash_sale/application/flash_sale_view_model_test.dart
test/features/flash_sale/data/repositories/flash_sale_repository_impl_test.dart
test/features/flash_sale/domain/usecases/flash_sale_usecases_test.dart
test/features/flash_sale/presentation/views/flash_sale_banner_view_test.dart
```

## Review follow-up fix (2026-08-27)

Addressed both Task 3 review findings:

1. An active Flash Sale campaign with no available items now renders an
   explicit `flash_sale_empty` state instead of collapsing the section.
2. The ViewModel now requests and aggregates inventory from every active
   campaign, while retaining the first active campaign only as the banner
   header/countdown context. Every displayed line remains the server-issued
   `FlashSaleItemEntity`, including its authoritative `id` for checkout.

TDD evidence:

```text
fvm flutter test test/features/flash_sale/application/flash_sale_view_model_test.dart test/features/flash_sale/presentation/views/flash_sale_banner_view_test.dart
Initial result: 2 failures — only campaign 11 was requested; flash_sale_empty was absent.

fvm dart format lib/features/flash_sale/application/flash_sale_view_model.dart lib/features/flash_sale/presentation/views/flash_sale_banner_view.dart test/features/flash_sale/application/flash_sale_view_model_test.dart test/features/flash_sale/presentation/views/flash_sale_banner_view_test.dart
fvm flutter test test/features/flash_sale/application/flash_sale_view_model_test.dart test/features/flash_sale/presentation/views/flash_sale_banner_view_test.dart
Result: All tests passed (7 tests).

fvm flutter test test/features/flash_sale test/features/catalog test/features/cart
Result: All tests passed (52 tests).

fvm flutter analyze lib/features/flash_sale test/features/flash_sale test/features/catalog test/features/cart
Result: No issues found.

git diff --cached --check
Result: exit 0, no whitespace errors.
```

Focused fix commit:

```text
4abb271abea50fa4c324e2ba59a020acc960db35 fix(app): show complete flash sale state
```

The commit stages only these Task 3 fix files:

```text
lib/features/flash_sale/application/flash_sale_view_model.dart
lib/features/flash_sale/presentation/views/flash_sale_banner_view.dart
test/features/flash_sale/application/flash_sale_view_model_test.dart
test/features/flash_sale/presentation/views/flash_sale_banner_view_test.dart
```

## Scoped re-review follow-up (2026-08-27)

The multi-campaign aggregation is now resilient per campaign. Failed campaign
item requests are skipped while the first failure is retained in
`partialErrorMessage`; successful server-issued `FlashSaleItemEntity` values
continue to render. A full retryable `errorMessage` is emitted only when no
available inventory remains after filtering every successful response.

TDD and validation evidence:

```text
fvm flutter test test/features/flash_sale/application/flash_sale_view_model_test.dart
Initial result: 1 failure — a campaign 12 failure set errorMessage and discarded campaign 11 item 71.

fvm dart format lib/features/flash_sale/application/flash_sale_state.dart lib/features/flash_sale/application/flash_sale_view_model.dart test/features/flash_sale/application/flash_sale_view_model_test.dart
fvm flutter test test/features/flash_sale/application/flash_sale_view_model_test.dart
Result: All tests passed (3 tests).

fvm flutter test test/features/flash_sale test/features/catalog test/features/cart
Result: All tests passed (53 tests).

fvm flutter analyze lib/features/flash_sale test/features/flash_sale test/features/catalog test/features/cart
Result: No issues found.

git diff --cached --check
Result: exit 0, no whitespace errors.
```

Focused fix commit:

```text
c72877368a8374a49b9b657b8990163284757339 fix(app): preserve partial flash sale inventory
```

The commit stages only these Task 3 files:

```text
lib/features/flash_sale/application/flash_sale_state.dart
lib/features/flash_sale/application/flash_sale_view_model.dart
test/features/flash_sale/application/flash_sale_view_model_test.dart
```
