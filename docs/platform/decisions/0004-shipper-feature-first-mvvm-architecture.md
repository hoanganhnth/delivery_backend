# 0004 Shipper Feature-First MVVM Architecture

Date: 2026-08-09

## Status

Accepted

## Context

The React Native shipper app has a testable Redux/service seam, but production
screens currently mix rendering, form state, navigation, alerts, timers,
native SDK calls and Redux dispatch. Some native services import Redux actions,
which reverses the dependency direction. The app needs a durable structure
that keeps Gateway and fulfilment behavior stable while making every user
action auditable and testable.

## Decision

- Organize code by feature: `auth`, `shipper`, `delivery`, `tracking` and
  `notifications`, with each feature owning domain, data, state/application and
  presentation folders.
- Keep Redux Toolkit as the shared application-state container.
- A production screen follows `Route -> ViewModel -> View`. Views receive typed
  `ViewState` and a discriminated-union `ViewEvent` sink; they do not access
  Redux, navigation, services, native SDKs, storage or alert APIs.
- ViewModels own user actions and presentation effects, accessing navigation,
  feedback, scheduler, media, GPS, WebSocket and push through narrow typed
  ports injected at the composition root.
- Repositories and native adapters return domain objects and do not import
  Redux/presentation. Existing Gateway contracts, state transitions and
  fail-closed parsers remain authoritative.
- Shared visual primitives use existing values first; this decision does not
  authorize UX or hidden-capability changes.

## Alternatives Considered

1. Replace Redux with another state-management library. Rejected because the
   current Redux store and test registry are a useful seam; replacing both
   state and screen ownership would multiply migration risk.
2. Keep current screen-owned dispatch/navigation and only split files. Rejected
   because it leaves user actions and native side effects untestable from a
   clear boundary.
3. Apply full Clean Architecture boilerplate to every simple read screen.
   Rejected because empty use-case layers reduce clarity; use cases are added
   where business rules or multi-port orchestration need them.

## Consequences

Positive:

- User actions have a single owner and pure Views can be tested without global
  providers or native mocks.
- Native/network dependencies point inward through ports, preventing service to
  Redux coupling.
- Feature ownership makes future work and parallel maintenance more legible.

Tradeoffs:

- Migration temporarily uses thin compatibility re-exports and requires more
  focused tests.
- Route adapters and ViewModel contracts add files around very small screens.

## Follow-Up

- See the completed migration and validation record at
  `docs/plans/completed/shipper-app-feature-first-mvvm-refactor.md`.
- Document import-boundary checks and the final feature map in the app README.
