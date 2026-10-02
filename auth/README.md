# Auth architecture

Auth owns authentication identity, onboarding lifecycle and device credentials.
The service is built from root-level layers:

| Layer | Responsibility |
| --- | --- |
| `domain` | Framework-free identity/session facts, rules and rejection types |
| `application-api` | Behavior-free use-case/adapter interfaces and data records |
| `application` | Framework-free workflows and transaction decisions |
| `infrastructure` | HTTP, JPA, JWT/Google/Firebase, Kafka, email and Spring composition |
| `boot` | Entrypoint, runtime configuration and integration proof |

Production controllers now use core workflows for password registration/login,
refresh rotation/reuse/logout, active-device sessions/revocation, registration
recovery/retention, registration admission, operator ADMIN/SHIPPER provisioning,
social login, Firebase chat token issuance, account block/unblock with projection
retry, `identity.profile.created` linkage with inbox deduplication, safe account
lookup, simulation actor binding with signed fencing tokens, password reset,
email verification and security-token retention. Profile
binding and lifecycle-version updates use shared core rules. Persistence adapters retain the existing schema,
pessimistic locks and transaction order; crypto and wire DTOs remain adapter concerns.

The old Auth business facade and security-token service have been removed.
The packaged JAR has passed HTTP, PostgreSQL registration and restart checks.
The Kafka/PostgreSQL proof covers profile-event consumption, inbox deduplication
and status-outbox publication. The Compose image and resolved runtime
dependency boundaries have also been verified. Follow
`docs/plans/active/service-architecture-consolidation.md` for the broader
service consolidation.

From the reactor root, run `mvn -B -pl :auth-service -am clean verify`.
PostgreSQL integration proofs require Docker and must run without skips.
