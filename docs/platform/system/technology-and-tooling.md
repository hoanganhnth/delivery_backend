# Technology and Tooling Inventory

> Status: as-built dependency and toolchain guide, checked 2026-08-08. This is
> the concise reconstruction inventory, not a replacement for the owning
> `pom.xml`, `package.json`, `pubspec.yaml`, lockfile or container manifest.
> Pin direct/transitive versions from those source files when reproducing a
> build; do not upgrade a component merely because a newer release exists.

## Purpose and reading rule

An engineer or AI recreating the platform needs both the architectural role of
a technology and the manifest that fixes its version. This guide records the
first; its source links record the second. A dependency appearing in a client
manifest or an optional backend module is not automatically an enabled MVP
capability.

## Backend build and runtime

| Concern | Current implementation | Reconstruction rule / authority |
| --- | --- | --- |
| Language/toolchain | Java 17, Maven multi-module reactor | Use the `java.version` values and Maven parent/module graph in [backend parent POM](../../../pom.xml); backend CI also sets Temurin 17. |
| Application framework | Spring Boot 3.5.15 | Each executable module declares the Boot parent; see [Gateway POM](../../../api-gateway/pom.xml). |
| Cloud/runtime framework | Spring Cloud 2025.0.3; Config Client, Eureka Client and LoadBalancer | Shared [runtime platform starter](../../../runtime-platform-starter/pom.xml) is the common bootstrap dependency. Do not assume Kubernetes DNS has replaced Eureka. |
| Public edge | Spring Cloud Gateway WebFlux plus reactive Redis | [Gateway POM](../../../api-gateway/pom.xml) and exact route/config source are authoritative. |
| Resource HTTP services | Spring MVC/Web, Validation, Security, JPA | Individual service POMs own their exact dependencies; [Auth POM](../../../auth-service/pom.xml) is a representative web/JPA/security module. |
| Auth | Spring Security OAuth2 Resource Server/Nimbus through a shared starter; Auth signs RS256/JWKS | Use [auth-resource-server starter](../../../auth-resource-server-starter/pom.xml) and [security guide](./security.md); do not reintroduce Gateway token decoding. |
| Persistence/schema | PostgreSQL JDBC, Spring Data JPA and Flyway migrations per owning service | Migrations under each service's `src/main/resources/db/migration/` are schema authority. |
| Events | Spring Kafka; transactional outbox, retry/DLT and consumer receipt/dedup patterns | The technology alone does not give exactly-once behavior; preserve [event/data rules](./events-and-data.md). |
| Cache/realtime | Redis data/reactive clients, Redis GEO/PubSub, raw WebSocket | Redis is volatile operational state, not the source of truth for orders or money. |
| Search | Elasticsearch projection | Search indices are rebuildable projections, not primary business data. |
| Resilience | Resilience4j at audited HTTP boundaries; Spring Kafka retry/DLT | Preserve timeouts, fail-open/fail-closed policy and replay rules from code/runbooks. |
| Observability | Spring Actuator, Micrometer Prometheus registry, Micrometer tracing/OpenTelemetry OTLP, SLF4J | The shared [observability starter](../../../observability-starter/pom.xml) carries safe correlation propagation; see [observability operations](./operations/observability.md). |
| Code generation/ergonomics | Lombok, Maven compiler plugin | Annotation processing/version settings remain in the owning POM. |
| Tests | JUnit/Spring Boot Test, H2 for selected in-process tests, Testcontainers dependencies where declared | A declared dependency is not evidence that all production race/provider scenarios have been run. |

The reactor currently contains the three shared starters, Config/Eureka and the
domain/service modules listed in the [service catalog](./service-catalog.md).
Package the reactor before Docker builds: the shared [Dockerfile](../../../Dockerfile)
intentionally rejects a stale service JAR rather than compiling code implicitly
inside the final image.

## Local data plane and observability images

These image tags are the current local Compose topology, not a production
selection or an HA prescription.

| Local component | Image/version | Current mode / important limitation |
| --- | --- | --- |
| JVM image | `amazoncorretto:17-alpine` | Multi-stage artifact check then non-root runtime image. |
| PostgreSQL | `postgres:16-alpine` | One local server with logical database-per-service; no production HA/PITR claim. |
| Redis | `redis:7-alpine` | Local cache/GEO/PubSub/rate-limit instance; volatile state. |
| Kafka | `confluentinc/cp-kafka:7.4.0` | Single-node KRaft, plaintext local listeners and auto-created topics; not production replication/ACL policy. |
| Elasticsearch | `elasticsearch:7.17.10` | Single-node local search projection; version/support strategy needs an approved production decision. |
| OpenTelemetry Collector | `otel/opentelemetry-collector-contrib:0.132.0` | Local collector/debug exporter only; not durable trace retention. |
| Prometheus | `prom/prometheus:v3.5.0` | Local scrape/rules only. |
| Grafana | `grafana/grafana:12.1.0` | Local provisioned dashboard only. |

The complete ports, environment variables, resource limits, volumes and
optional-capability profile are in [docker-compose.yml](../../../docker-compose.yml).
Use the Compose/Kubernetes distinction in [operations](./operations/README.md):
these images must not be copied blindly into a production cluster.

## Customer Flutter application

| Concern | Current implementation | Authority |
| --- | --- | --- |
| SDK | Flutter 3.32.8 via FVM; Dart constraint `>=3.8.0 <4.0.0` | [.fvmrc](../../../../delivery_app/.fvmrc), [pubspec.yaml](../../../../delivery_app/pubspec.yaml) |
| State/navigation/network | Riverpod, GoRouter, Dio/Retrofit, JSON serialisation/build runner | [pubspec.yaml](../../../../delivery_app/pubspec.yaml) and [client guide](./clients.md) |
| Device/map/push | Mapbox, Geolocator, Firebase Core/Messaging/Crashlytics, local notifications, WebSocket channel | Credentials/configuration are deployment supplied and not documentation values. |
| Local persistence/UI | Hive/shared preferences, Freezed, Flutter Material/localisation and listed UI packages | Preserve only features actually enabled by the Gateway contract. |
| Native build | Android Gradle 8.12; iOS CocoaPods | [Gradle wrapper](../../../../delivery_app/android/gradle/wrapper/gradle-wrapper.properties), [Podfile](../../../../delivery_app/ios/Podfile) |

Firebase Auth/Firestore, video/livestream and other packages may be present in
the source tree. They do not make chat, livestream, payment or another hidden
capability part of the current supported MVP.

## Admin/restaurant web portal

| Concern | Current implementation | Authority |
| --- | --- | --- |
| Runtime/build | Node 22 in CI; Vite 8 | [package.json](../../../../delivery_web/package.json), [web CI](../../../../delivery_web/.github/workflows/ci.yml) |
| UI | React 19, React Router, Tailwind/PostCSS | [package.json](../../../../delivery_web/package.json) |
| Network/testing | Axios, Vitest, Testing Library, ESLint | `npm run verify` runs lint, unit tests, Gateway action-contract check and production build. |
| Boundary | `VITE_API_BASE_URL` points to the Gateway origin | [client guide](./clients.md) and [web README](../../../../delivery_web/README.md) |

## Shipper React Native application

| Concern | Current implementation | Authority |
| --- | --- | --- |
| Runtime/build | React Native 0.80.1, React 19.1, Node `>=18` (Node 20 in CI) | [package.json](../../../../shipper_app2/package.json), [shipper CI](../../../../shipper_app2/.github/workflows/ci.yml) |
| State/navigation/network | Redux Toolkit, React Navigation, Axios, TypeScript 5 | [package.json](../../../../shipper_app2/package.json) |
| Device/map/push | RN Mapbox, geolocation, Firebase Messaging, Android/iOS native projects | Mobile FCM/device connectivity is best-effort wake-up, never business truth. |
| Test quality gate | Typecheck, ESLint and Jest; debug Android build in CI | `npm run verify`; no emulator is implied by the CI build. |
| Native build | Android Gradle 8.14.1; iOS CocoaPods | [Gradle wrapper](../../../../shipper_app2/android/gradle/wrapper/gradle-wrapper.properties), [Podfile](../../../../shipper_app2/ios/Podfile) |

## CI, packaging and verification chain

| Repository | Required CI/build posture | Source of truth |
| --- | --- | --- |
| Backend | JDK 17 baseline, Maven test/package, API/Compose/Kubernetes/secret checks, Docker artifact/non-root checks; Trivy is advisory | [backend CI](../../../.github/workflows/ci.yml) |
| Flutter | Flutter analyse/test/coverage/debug APK; `.env` uses a non-secret CI placeholder | [Flutter CI](../../../../delivery_app/.github/workflows/flutter-ci.yml) |
| Web | Node 22, `npm ci`, lint/test/action-contract/build; Trivy advisory | [web CI](../../../../delivery_web/.github/workflows/ci.yml) |
| Shipper | Node 20, `npm ci`, typecheck/lint/Jest/debug Android build; Trivy advisory | [shipper CI](../../../../shipper_app2/.github/workflows/ci.yml) |

## Reconstruction and upgrade rules

1. Start by reproducing declared toolchain versions and direct dependencies;
   lockfiles/Gradle wrappers/pod configuration are part of the build contract.
2. Preserve the backend's Java 17/Boot/Cloud compatibility line before changing
   one module. The baseline verifier intentionally rejects an ambient JDK that
   does not match it.
3. Do not turn local Docker image tags into an immutable production release.
   Production needs registry digests, provider-selected data plane, secrets and
   observability decisions as documented in [deployment foundation](./operations/deployment-foundation.md).
4. Treat package presence as implementation material, not product authorization.
   The Gateway route inventory and feature flags decide what clients may use.
5. When changing any manifest, update this guide only if the architectural role
   or supported compatibility line changes; then refresh the offline reference
   bundle and run the owning repository's verification.

## Exact manifests to inspect

- [Backend reactor and module POMs](../../../pom.xml)
- [Backend Dockerfile](../../../Dockerfile) and [Compose topology](../../../docker-compose.yml)
- [Flutter package/toolchain manifest](../../../../delivery_app/pubspec.yaml)
- [Web package manifest](../../../../delivery_web/package.json)
- [Shipper package manifest](../../../../shipper_app2/package.json)
- [System source map](./rebuild/source-map.md)
