# Shipper App Architecture

## Ownership model

Every production screen follows:

```text
Route -> ViewModel -> View
```

- A `View` renders an immutable state and emits a discriminated `ViewEvent`.
  It must not import Redux, navigation, repositories, storage, native services,
  `Alert`, or runtime configuration.
- A `ViewModel` owns user events, local form state, feedback, navigation ports,
  timers, lifecycle effects and feature commands. It uses dependencies supplied
  through `AppRuntimeProvider` or the Redux store factory.
- A `Route` only adapts navigation and composes a ViewModel with a View.

## Directory map

```text
src/
  app/        composition root, navigation, store, bootstrap, overlay
  core/       HTTP/session/platform contracts and generic adapters
  shared/     UI primitives, tokens, formatters and pure helpers
  features/
    auth/
    delivery/
    notifications/
    shipper/
    tracking/
      domain/ data/ application/ platform/ state/ presentation/
```

`data` implements repository contracts. `platform` owns native SDK/resource
adapters. `application` may bridge a feature state command to a platform port;
neither layer imports a View or Redux presentation code.

## Runtime composition

`AppRuntimeProvider` supplies feedback, media picker, social identity, GPS,
lifecycle, scheduler, push runtime and tracking configuration. `createAppStore`
supplies repositories, clock, push session and location socket port. Production
instances are created only under `src/app/`.

The architecture checker (`npm run verify:architecture`) rejects legacy imports
and forbidden dependencies between feature layers. Add the appropriate focused
test whenever a ViewModel gains an event, timer, subscription or port.

## Contract invariants

- REST and raw location WebSocket use the configured Gateway and Bearer token.
- FCM is wake-only: an offer is recovered from
  `GET /api/deliveries/offers/current`.
- Do not alter delivery transitions, COD validation, Gateway payloads or hidden
  MVP capabilities as part of an app refactor.

See [SCREEN_POLICY.md](SCREEN_POLICY.md) and [AUTH_CONTRACT.md](AUTH_CONTRACT.md)
for product boundaries.
