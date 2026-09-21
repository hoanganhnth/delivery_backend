# Task 5 Fix Round 1 Report

## Files changed

- `__tests__/deliveryViewModelBoundaries.test.ts`
- `__tests__/mainMapProjection.test.ts`
- `src/features/delivery/presentation/viewmodels/useOfferViewModel.ts`
- `src/features/delivery/presentation/viewmodels/useActiveDeliveryViewModel.ts`
- `src/features/delivery/presentation/routes/MatchFoundRoute.tsx`
- `src/features/delivery/presentation/routes/ActiveDeliverySheetRoute.tsx`
- `src/features/tracking/presentation/viewmodels/useMainMapViewModel.ts`

## Port signatures

- `useOfferViewModel(input: OfferViewModelInput)`
- `OfferCommandPorts.acceptOrder(input: AcceptDeliveryRequest): Promise<CommandResult<Delivery>>`
- `OfferCommandPorts.rejectCurrentOffer(input: { orderId: number; reason: string }): Promise<CommandResult<void>>`
- `useActiveDeliveryViewModel(input: ActiveDeliveryViewModelInput)`
- `ActiveDeliveryCommandPorts.updateDeliveryStatus(input: { id: number; status: ShipperDeliveryTransition }): Promise<CommandResult<Delivery>>`
- `ActiveDeliveryCommandPorts.setCanUpdateStatus(value: boolean): void`
- `SchedulerPort` comes from `src/core/platform/ports.ts`

## TDD proof

- Red: `__tests__/deliveryViewModelBoundaries.test.ts` failed on the current `useOfferViewModel.ts` and `useActiveDeliveryViewModel.ts` app/runtime imports.
- Red: `__tests__/mainMapProjection.test.ts` failed because nested `steps` DTO values were still shared with the source read model.
- Green: both tests passed after route-level injection and deep step cloning.

## Validation

- `npm test -- --runInBand __tests__/deliveryViewModelBoundaries.test.ts __tests__/mainMapProjection.test.ts` passed.
- `npm test -- --runInBand __tests__/MatchFoundPopup.test.tsx __tests__/BottomSheetDelivery.test.tsx` passed.
- `npm run typecheck` passed.
- `npm run lint` passed.
- `npm run verify:architecture` passed.
- `git diff --check` passed.

## Concerns

- Jest reported an existing open-handle warning after the route suites; the suites still passed and the warning did not point at the changed files.
- The new command-result ports are thin Redux thunk adapters in the route containers; that keeps the viewmodels app-independent, but it is a small amount of repeated wiring.
