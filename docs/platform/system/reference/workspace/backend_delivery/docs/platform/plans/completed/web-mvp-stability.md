# Delivery Web MVP stability

## Outcome

Make the React admin/restaurant portal reliable across session restoration,
local-time coupon creation, failed reads, repeated mutations, and mobile/tablet
navigation without changing the Gateway/API contract or opening hidden MVP
capabilities.

## Approach

- Preserve the current Bearer + refresh-token localStorage boundary and make
  bootstrap use the existing Axios single-flight refresh path.
- Add strict TypeScript checking to the repository verification gate.
- Normalize admin async read/mutation states and add retryable inline failures.
- Add responsive mobile navigation while keeping desktop sidebars and existing
  routes.
- Align the feature documentation with the visible MVP router and action matrix.

## Progress

- [x] Session recovery and typecheck gate
- [x] Coupon local-time handling and mutation guards
- [x] Admin/restaurant error and retry behavior
- [x] Responsive navigation and documentation alignment
- [x] Focused tests and full verification

## Validation target

- `npx tsc --noEmit` passes.
- `npm run verify` passes with session, coupon, error/retry, duplicate-submit,
  and responsive navigation tests.
- Worktree changes remain limited to the web client and this execution plan.

## Validation record

- `npm run verify`: passed; lint, typecheck, 51 tests, action-contract gate and
  Vite production build all passed.
- `npm run test:coverage`: passed; 68.08% statements, 60.11% branches,
  62.99% functions and 69.14% lines.
- Build emitted only the existing Browserslist freshness warning.

## Result

Implemented session recovery/retry, strict typecheck gating, local-time coupon
handling, mutation locks, retryable admin/restaurant read errors, responsive
mobile drawers, and MVP feature-documentation alignment. No backend contract or
localStorage token boundary was changed.

## Decisions

- Do not migrate tokens to HttpOnly cookies in this change.
- Network/5xx bootstrap failures retain the stored session and expose retry;
  only an unrecoverable authentication failure clears the session.
- Mobile navigation uses an in-app accessible drawer with no new dependency.
