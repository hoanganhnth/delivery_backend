# Portal preview with production UI

## Outcome

DEV-only Admin and Restaurant preview routes now render the existing
production pages, layouts, providers, dialogs, forms, and responsive CSS with
deterministic in-memory fixtures. Authenticated production routes and backend
data remain unchanged.

## Delivered

- Added preview-aware navigation so production links and redirects remain
  inside `/preview/admin/*` and `/preview/restaurant/*`.
- Added fixture-backed implementations for auth, restaurant, menu, order,
  admin, promotion, flash-sale, livestream, address, session, clock, and
  notification ports.
- Added preview coverage for all current Admin and Restaurant operational
  pages, including catalog import and livestream.
- Kept the preview workbench, phone mode, responsive desktop mode, theme
  selector, and in-memory reset behavior.

## Validation

- `npm run verify:ci` passed: lint (0 errors, two pre-existing warnings),
  typecheck, `npm test` (28 files / 142 tests), action contracts, and
  production build.
- `npx vitest run src/modules/portal-preview` passed: 2 files / 8 tests.
- Browser smoke passed for Admin and Restaurant dashboard/order previews at
  phone and desktop widths. Full E2E was intentionally not run.

## Recovery

The change is additive and DEV-only. Remove the preview branch in
`src/App.tsx`, the preview navigation adapter, and `src/modules/portal-preview/`
to restore the previous preview behavior; no backend or database rollback is
required.
