# Shipper App

React Native app for a shipper to recover offers, accept or refuse delivery,
update the approved delivery lifecycle, share location and manage their profile.

## Architecture

The app is feature-first MVVM. A production screen is always:

```text
Route -> ViewModel -> View
```

Views are UI-only (`state + onEvent`). ViewModels own user actions and effects;
repositories/native adapters are injected at the app composition root. See
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for ownership rules and the folder
map.

## System boundary

REST and the raw location WebSocket go through the configured Gateway with the
stored Bearer token. Push is wake-only: the app recovers the canonical offer via
`GET /api/deliveries/offers/current`. It never calls private service ports or
uses an Internal-Token.

The refactor must not change Gateway payloads, delivery transitions, COD policy,
or hidden MVP capability. See [docs/SCREEN_POLICY.md](docs/SCREEN_POLICY.md).

## Development

```sh
npm install
npm start
npm run android
```

For iOS, install pods after dependency changes and run `npm run ios`.

## Validation

```sh
npm run verify
npm run verify:coverage
npx react-native build-android --mode=debug
```

Test conventions and dependency fakes are documented in
[TESTING.md](TESTING.md).
