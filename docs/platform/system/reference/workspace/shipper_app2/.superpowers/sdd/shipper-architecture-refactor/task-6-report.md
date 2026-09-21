# Task 6 Report

## Outcome

Task 6 is implemented on the shipper worktree. Views now consume immutable presentation DTOs and emit discriminated events only. Presenter code owns formatting, status labels, star counts, accept/advance eligibility, and semantic tones. Shared UI gained only the two generic primitives that had real multi-screen reuse.

## Files Changed

- Presentation DTO / presenter layer:
  - `src/features/delivery/presentation/contracts/ActiveDeliveryContract.ts`
  - `src/features/delivery/presentation/contracts/DeliveryHistoryContract.ts`
  - `src/features/delivery/presentation/contracts/OfferContract.ts`
  - `src/features/delivery/presentation/contracts/OrderDetailContract.ts`
  - `src/features/delivery/presentation/detailPresenter.ts`
  - `src/features/delivery/presentation/historyPresenter.ts`
  - `src/features/delivery/presentation/activeDeliveryPresenter.ts`
  - `src/features/delivery/presentation/offerPresenter.ts`
  - `src/features/shipper/presentation/contracts/RatingContract.ts`
  - `src/features/shipper/presentation/profilePresenter.ts`
  - `src/features/shipper/presentation/viewmodels/useActiveDeliveryViewModel.ts`
  - `src/features/delivery/presentation/viewmodels/useOfferViewModel.ts`
  - `src/features/delivery/presentation/viewmodels/useDeliveryHistoryViewModel.ts`
  - `src/features/delivery/presentation/viewmodels/useOrderDetailViewModel.ts`
  - `src/features/shipper/presentation/viewmodels/useShipperRatingViewModel.ts`

- View layer:
  - `src/features/delivery/presentation/views/MatchFoundView.tsx`
  - `src/features/delivery/presentation/views/ActiveDeliverySheetView.tsx`
  - `src/features/delivery/presentation/views/DeliveryHistoryView.tsx`
  - `src/features/delivery/presentation/views/CancelReasonView.tsx`
  - `src/features/delivery/presentation/views/OrderDetailView.tsx`
  - `src/features/shipper/presentation/views/ShipperRatingView.tsx`

- Shared UI:
  - `src/shared/ui/components/ListActionRow.tsx`
  - `src/shared/ui/components/StatusBadge.tsx`
  - `src/shared/ui/components/index.ts`
  - `src/shared/ui/theme/tokens.ts`

- Tests:
  - `__tests__/task6Presenters.test.ts`
  - `__tests__/task6SharedUi.test.tsx`
  - `__tests__/task6Views.test.tsx`

## DTO / Presenter Decisions

- `MatchFoundViewState` now carries a projected offer DTO, not the raw offer snapshot.
- `ActiveDeliverySheetViewState` now carries projected order labels, tone keys, and addresses only.
- `DeliveryHistoryItem` now carries `deliveryId`, labels, and a semantic tone key; it no longer exposes the delivery entity.
- `ShipperRatingPresentation` now carries `orderLabel`, `starCount`, and `createdAtLabel`.
- `CancelReasonViewState` now owns `needsOtherReason` and `canConfirm`.
- `DeliveryStatusPresentation` now exposes a tone key instead of raw colors.
- `semanticTones` was added to shared tokens so feature presenters/views can map status semantics without hard-coded color literals.

## Shared Component Decisions

- Added `StatusBadge` because it has real reuse in delivery history, active delivery, and order detail.
- Added `ListActionRow` because it is used for both cancel reasons and batch stop rows.
- Kept address/document controls feature-local.
- Reused `AppHeader`, `AsyncStateView`, and `AppButton` where their contracts matched.

## TDD Evidence

- Red:
  - `__tests__/task6Presenters.test.ts` failed first on raw-color / legacy-shape expectations.
  - `__tests__/task6SharedUi.test.tsx` failed because `ListActionRow` and `StatusBadge` did not exist.
  - `__tests__/task6Views.test.tsx` failed because the views still consumed old snapshot shapes and derived state.
- Green:
  - All three Task 6 test files pass after the presenter/view rewiring.

## Validation

- Focused Task 6 tests: passed.
  - `npm test -- --runInBand __tests__/task6Presenters.test.ts __tests__/task6SharedUi.test.tsx __tests__/task6Views.test.tsx`
- Typecheck: passed.
  - `npm run typecheck`
- Lint: passed.
  - `npm run lint`
- Architecture boundary check: passed.
  - `npm run verify:architecture`
- Relevant broader tests: passed.
  - `npm test -- --runInBand __tests__/BottomSheetDelivery.test.tsx __tests__/MatchFoundPopup.test.tsx __tests__/OrderDetailViewModel.test.tsx __tests__/ShipperViewModels.test.tsx __tests__/SecondaryViewModels.test.tsx __tests__/deliveryViewModelBoundaries.test.ts __tests__/presentationContracts.test.ts`
- Full test suite: passed.
  - `npm test -- --runInBand`

## Unresolved Concerns

- No functional blockers remain.
- `ListActionRow` and `StatusBadge` are intentionally minimal; if later screens want richer variants, they should be added only when a second real consumer appears.
