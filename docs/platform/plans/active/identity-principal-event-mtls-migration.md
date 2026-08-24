# Execution Plan: Identity Principal And Event Migration

Date: 2026-08-14

## Status

Active — code-first scope. The Auth/User/JWKS source foundation is additive and
compile/static-validated. Kubernetes, Compose rollout automation, Kafka
topology provisioning, dashboards, SLO gates and production cutover are
explicitly deferred; they are not implementation tasks in this plan's current
iteration.

## Outcome

The platform has one stable identity key, `principalId = auth_account.id`.
Auth owns identity lifecycle and signs JWTs; resource services verify Auth's
JWKS locally and authorize with the claims. Password registration becomes a
durable Auth → User → Auth event workflow, while each business service retains
its own profile and domain IDs. The migration can be activated and reversed
per capability without a fleet-wide restart or a destructive database change.

## Context

- Repository architecture and current service boundary:
  `backend_delivery/docs/product/overview.md` and
  `backend_delivery/docs/services/auth_and_users.md`.
- Runtime switch order and rollback detail:
  `backend_delivery/docs/runbooks/identity-principal-event-rollout.md`.
- The implementation already contains additive tables, JWT dual claims,
  JWKS validation, outbox/inbox, service-specific Kubernetes flags, and
  rollout metrics. It has **not** been proved against live Kafka/PostgreSQL.
- `principalId` is not a profile or domain ID: `users.id`, `shipper.id`,
  `restaurant.id`, order IDs, and ledger IDs keep their existing ownership.
- Preserve unrelated user work in `delivery_app/coverage/lcov.info`.

## Current Code-First Scope (2026-08-14)

This section overrides the release-engineering execution sections below for
the current iteration. They are retained as historical rollout notes, not as
work to perform now.

### Included now

1. **Finish the public code contract.** Keep `principalId` canonical while
   retaining `authId` as a compatibility alias. Document and enforce the
   two-request public flow: Auth creates/resumes an identity and produces a
   short-lived handoff; User verifies it through Auth JWKS and creates the
   profile idempotently; Auth links it only from `identity.profile.created`.
2. **Make client registration recoverable without happy-path polling.** The
   Flutter client must retain the `registrationHandle`, expiry and lifecycle
   returned by Auth. It calls User immediately once; it must not poll during a
   successful registration. If the process/network fails after Auth accepted
   the request, recovery queries `GET /auth/registrations/{handle}` only when
   the user resumes the interrupted flow.
3. **Close source-level correctness gaps in that flow.** Verify idempotency,
   claim validation, email canonicalisation and lifecycle transitions at the
   code and database-contract level. Fix only defects found in these paths.
4. **Migrate service code to explicit JWT claims.** Each resource boundary
   reads `principal_id`, `legacy_user_id` and
   `identity_claims_version=1`; no authorization decision reads `sub` or a
   Gateway-injected identity header. Preserve dual-read compatibility until a
   later approved contraction.
5. **Keep the plan and API documentation truthful.** Source contracts and
   client behaviour must describe asynchronous Auth linking accurately.

### Deferred deliberately

- Kubernetes manifests, Helm/GitOps scripts, rollout runners, ConfigMaps,
  image promotion, SRE dashboards/alerts and deployment waves.
- Kafka topic/ACL/DLT provisioning and production metrics gates. The outbox,
  inbox and event-handling code remain in scope; operating their infrastructure
  does not.
- Compose/live-runtime/E2E execution. The user runs the one final acceptance
  gate after code review; the implementation work only uses compile/static
  validation when a changed module requires it.
- Removing dual claims, legacy columns, or compatibility readers. That needs a
  separately approved data-retention and production-evidence decision.

### Ordered implementation backlog

| Order | Code deliverable | Completion evidence (not user test/E2E) |
| --- | --- | --- |
| C1 | Auth/User API contract is canonical: response exposes `principalId`, `registrationHandle`, `expiresAt`, and lifecycle; User success wording never claims the asynchronous link is done. | DTO/controller/service inspection plus module compile. |
| C2 | Flutter carries the complete Auth registration response through its data/domain boundary and invokes the User registration request immediately. | Generated DTOs and static analysis. |
| C3 | Add an explicit interrupted-registration result and one-shot recovery query in Flutter. It is failure-only, never an automatic polling loop. | Contract fixture and static analysis. |
| C4 | Resolve the recovery credential policy: current app does not persist any continuation secret. The user retries Auth with the same credentials; Auth resumes `PENDING_PROFILE` idempotently and returns a fresh handoff. Do not store the provisioning JWT or handle in Hive/plain preferences. | Recorded decision and reviewed code boundary. |
| C5 | Audit/fix remaining resource-service authorization code to use strict explicit claims only, including WebSocket handshakes and public-route `permitAll` alignment. | Static identity audit and changed-module compile. |
| C6 | User-owned final acceptance: exercise successful registration, duplicate/retry, app interruption/recovery, expiry, Auth→User→Auth convergence, block/unblock projection and login. | User-provided test/runtime result; not run by the implementation agent. |

### Source flow to preserve

```mermaid
sequenceDiagram
    participant C as Client
    participant A as Auth service
    participant U as User service
    participant K as Kafka / outbox

    C->>A: POST /auth/register
    A-->>C: principalId, registrationHandle, expiry, provisioning JWT
    C->>U: POST /users/registrations (provisioning JWT)
    U->>U: Verify Auth JWKS; idempotently create profile
    U->>K: identity.profile.created (outbox)
    U-->>C: Profile created; linking is processing
    K->>A: identity.profile.created
    A->>A: Link auth_account.user_id; transition lifecycle
    Note over C,A: Only after interruption, client queries registrationHandle.<br/>No polling in the normal path.
```

## Scope

In scope:

- Source changes needed by C1–C5 above: Auth/User registration contracts,
  Flutter recovery state, JWKS resource validation and explicit-claim
  authorization code.
- Additive schema and source-level event contracts where a code correctness
  defect requires them.
- Compile/static validation of modules changed by this iteration.

Out of scope:

- Kubernetes, Compose, Kafka broker, observability, deployment/rollout and
  production cohort activities; see **Deferred deliberately** above.
- Replacing internal service authentication with mTLS/workload identity. That
  is a separate platform programme.
- Reusing `principalId` as `shipper.id`, an analytics aggregation key, or the
  Livestream Agora numeric UID.
- Changing social login or operator provisioning semantics: they currently
  rely on synchronous Auth → User completion and need a separate product
  decision because immediate-token behaviour would change.

## Rollout Principles

This follows the operating model appropriate for a large multi-service system:

1. **Expand, then migrate, then contract.** Schema and event contracts are
   additive; a rollout only changes one reader/writer mode at a time; deletion
   happens in a later release.
2. **Separate deploy from enable.** Deploy dormant code first. A ConfigMap
   change restarts only the owning Deployment, never the whole platform.
3. **One capability and one blast radius per wave.** Auth profile consumption,
   User relay, Auth status relay, each Shipper enforcement point, and `sub`
   are independent switches. A failed gate is rolled back by its flag.
4. **Runtime data is the promotion authority.** Pod readiness alone is not
   proof. Outbox age, Kafka lag, retry/DLT, lifecycle convergence and legacy
   fallback rate decide whether the next step is safe.
5. **No test execution during implementation/release preparation.** Do only
   compile/static/manifest checks when code or manifests change. The single
   runtime and E2E test gate is intentionally owned and run by the user at the
   end.

## Fast-Track Production Release Plan

This is intentionally optimised for delivery speed without a fleet-wide
cutover, in the same shape used by a large multi-cell platform: release the
dormant capability once; activate the durable identity path; open traffic by a
small number of deterministic cohorts; and migrate ownership cells in parallel.
It has two independent tracks:

| Track | What it proves | Does not wait for |
| --- | --- | --- |
| **A. Identity platform** | JWKS, Auth → User → Auth registration, lifecycle events and explicit JWT claims work as a platform. | Principal ownership remediation in every business service. |
| **B. Business ownership** | A particular service can stop reading its legacy profile ID and fail closed on an unmigrated row. | Registration rollout or JWT `sub` compatibility cutover. |

`sub` is a compatibility field, not an authorization key. Therefore R5 does
**not** wait for every R4 migration: all resource services already authorize
from `principal_id`/`legacy_user_id`. It waits only for the explicit-claim
compatibility audit and healthy Track A operational signals. This removes an
unnecessary serial dependency while retaining a direct rollback of Auth token
issuance.

A metric gate is an operational safety check, **not** a request to run a test
suite. During this plan, no unit, integration, Compose, Kafka or E2E test is
run by the implementation/release operator. The user runs the one formal
acceptance/E2E gate at the end, after R5. Compile/static/manifest checks are
only repeated when a source or manifest change makes them necessary.

### Release-train calendar

The release owner may run these trains back-to-back once their listed runtime
signals are clean. There is no artificial weekly release boundary. A failed
gate holds or rolls back only its own capability; it does not reset a healthy
earlier train.

| Train | Fast path | Scope of change | Stop / rollback boundary |
| --- | --- | --- | --- |
| **T0** | Additive foundation | Images, Flyway, topics/ACLs, dashboards; all flags remain off. | Normal image/GitOps rollback; no customer behaviour changed. |
| **T1** | Durable event plumbing | R1 + R2: profile relay/consumer, lifecycle consumers, bootstrap, Auth relay. | Disable only the last relay/consumer flag; preserve outbox, inbox and receipts. |
| **T2** | Registration and JWT compatibility | R3 cohorts, then R5 Auth access-token `sub` issuer mode. | Set admission cohort to `0`, or set only Auth `sub` mode back to legacy. |
| **T3** | Parallel ownership hardening | Every R4 cell independently, ordered only within its dependency chain. | Turn off the owning service's strict flag; do not roll back Track A. |

### Fast execution sequence

1. **T0 — one immutable additive release.** Build/promote the already
   compile/static-validated service image digests, apply only additive Flyway
   migrations, create Kafka source topics/ACLs and retry/DLT topology, and
   publish dashboards/alerts. All flags stay false. This is the sole shared
   deployment; every later change is a narrow ConfigMap/GitOps revision plus
   restart of the owning Deployment.
2. **T1 — activate pipes in dependency order, without a manual test pass.**
   Enable Auth profile consumption and User profile relay; wait for bootstrap
   pending to drain and Auth consumer lag/retry/DLT to be clean. Enable User
   and Shipper lifecycle consumers in parallel; once assigned and clean, run
   the Auth status bootstrap and then its outbox relay. The evidence is
   dashboards and consumer/outbox state, not a bespoke registration exercise.
3. **T2a — open registration with fewer, meaningful cohorts.** Start at
   allowlist-only (`0%`), then `5%`, `25%`, and `100%`. The HMAC bucket makes a
   customer remain in the same cohort across retries. At each hold, inspect
   registration admission/latency, Auth/User outbox age, consumer lag, retry/
   DLT and HTTP error deltas. If any is unhealthy, return to `0%` immediately
   and let accepted registrations drain; never restore a synchronous
   registration RPC. Use a longer observation at 25% when traffic is low or
   has a known peak cycle; the release owner records the observed traffic and
   decision rather than pretending a fixed clock proves safety.
4. **T2b — JWT subject cutover as a small Auth-only compatibility release.**
   After Track A is healthy and the explicit-claim compatibility audit covers
   every deployed HTTP/WebSocket resource service, issue access tokens with
   `sub=principalId`. Do not wait for T3: ownership flags still use explicit
   claims. Observe for 20 minutes (access-token TTL plus clock skew); a
   regression rolls back only `AUTH_JWT_ACCESS_TOKEN_SUBJECT_MODE`. Refresh
   tokens deliberately retain legacy `sub` during their seven-day lifetime.
5. **T3 — run ownership cells concurrently.** Start all cells in dual mode and
   remediate local mappings concurrently. Promote only locally independent
   cells in parallel: Notification, Settlement, Promotion and Flash Sale.
   Keep dependency chains sequential: Shipper → Tracking → Delivery and
   Restaurant → Order. A strict flag is enabled only after its local parity,
   fallback, lag/retry/DLT and client-error gate is clean; its rollback affects
   only that service. The seven-day zero-fallback observation remains the
   production hardening criterion, but it no longer delays T2/R5.
6. **Final user-owned acceptance gate — once, last.** After R5 and every T3
   cell committed to this release is enabled, the user runs the selected
   production-like suite and attach-only Compose proof. Results decide whether
   to retain the release or execute the scoped rollback; they are not run by
   the rollout operator. Further ownership cells may be scheduled as a new
   release train rather than expanding this final gate indefinitely.

### R0 — One additive foundation release

Goal: ship the completed code, schema and telemetry once; do not expose a new
customer path yet.

1. Freeze the current source into immutable service image digests. Apply only
   additive Flyway migrations and deploy the normal release train. Do not run a
   cross-database backfill or rewrite legacy ownership.
2. Keep every identity capability flag off. In particular,
   `AUTH_PUBLIC_REGISTRATION_ENABLED=false` returns `503` before an Auth-only
   identity can be created.
3. Before a later flag change, provision the dashboard/alerts and prove the
   per-service rollback mechanism: GitOps revision plus a restart of only the
   owning Deployment. A ConfigMap update alone is not a rollout.

Promotion gate: migrations are recorded, pods are ready, and the required
outbox/lag/DLT/fallback/HTTP metric series exist. Stop on increasing outbox age,
any identity DLT, growing consumer lag, or a material 401/403/5xx regression.

### R1 — Rehydrate the registration path with no public traffic

Goal: make historical and future Auth → User → Auth profile events durable
before accepting a single new password registration.

1. Enable `AUTH_IDENTITY_EVENTS_ENABLED` on **Auth only**.
2. Enable `USER_IDENTITY_OUTBOX_RELAY_ENABLED` on **User only**. It seeds the
   idempotent `identity.profile.created` outbox from User-local
   `principal_id ↔ users.id` mappings and relays new profiles transactionally.
3. Wait for the User bootstrap-pending gauge to reach zero, Auth consumer
   assignment/lag to be healthy, and the related retry/DLT/outbox-age signals
   to remain clean. This is a data-drain gate, not a manual SQL reconciliation.

Rollback: keep these two recovery components on unless the deployment itself
is unsafe. Registration is still closed, so there is no new public blast
radius; all accepted historical events remain replayable.

### R2 — Bootstrap lifecycle projections before publishing live changes

Goal: converge blocks/unblocks through Kafka without missing accounts created
before the migration.

1. Enable `USER_IDENTITY_EVENTS_ENABLED` and
   `SHIPPER_IDENTITY_EVENTS_ENABLED`; these are two independent consumer cells
   and may roll out in parallel after their own readiness checks.
2. When both groups have assigned partitions and stable zero lag, enable
   `AUTH_IDENTITY_STATUS_BOOTSTRAP_ENABLED`. Auth writes one retained snapshot
   per linked identity using its local receipt table.
3. Wait for Auth bootstrap-pending to reach zero and for Auth outbox age,
   retry/DLT and consumer lag to remain healthy. Only then enable
   `AUTH_IDENTITY_OUTBOX_RELAY_ENABLED` on Auth.

Rollback: stop Auth's status relay first. Keep consumer receipts, bootstrap
receipts, outbox rows and projections so replay remains possible. The old
Auth-to-User status sync is a status-only fallback rail; it is not a
registration fallback.

### R3 — Open password registration as a traffic cell

Goal: increase real traffic without turning an identity migration into a
platform-wide release.

1. Deliver the Auth-only canary Secret (`allowlist`, `hash-key`), then enable
   the registration master flag at 0%: only approved staff/allowlisted accounts
   can enter.
2. Promote the deterministic HMAC cohort through 5%, 25%, and 100%.
   Do not attach fixed time promises to a cohort: the named release owner moves
   it only after the agreed observation window has clean admission,
   provisioning-latency, outbox, lag, DLT and HTTP-error signals.
3. At any failure, change cohort percentage to `0` first. Leave R1/R2 enabled
   until already-admitted registrations converge; never reintroduce a
   synchronous User → Auth public-registration RPC.

This is the only customer-facing expansion in the core identity rollout. It
keeps the blast radius bounded by cohort, not by region-wide or fleet-wide
traffic.

### R4 — Principal ownership by independent service cell

Goal: migrate authorization safely in parallel with normal product delivery.

1. Make each owner an independent release cell: first Shipper → Tracking →
   Delivery mapping, then Restaurant/Order, Notification, Settlement,
   Promotion and Flash Sale. A cell follows the same fixed sequence:
   dual-write → local idempotent seed/backfill → parity gate → principal-first
   read with measured fallback → fail-closed enforcement.
2. Do not block R3 on every business cell. The identity registration and
   lifecycle platform can operate with explicit dual JWT claims while each
   service completes its own data remediation.
3. Backfill only when the local legacy profile and principal mapping agree.
   Put everything else in a remediation queue; never derive a principal from
   `shipper.id` or another domain ID.
4. A cell earns enforcement after seven consecutive days with zero
   `delivery_identity_legacy_fallback_total{service,surface}`, zero relevant
   DLT/lag, and recorded projection parity. Roll back only that cell to dual
   fallback if a gate fails.

### R5 — JWT subject cutover, then the one user-owned final test gate

Goal: finish the compatibility change without a “flag day” and defer formal
test execution to the user as requested.

1. After Track A is healthy and the deployed resource-service compatibility
   audit confirms every boundary uses explicit claims, make Auth issue
   `sub = principalId`, while retaining `principal_id`, `legacy_user_id` and
   `identity_claims_version=1`. R4 service cells do not block this issuer-only
   cutover because they do not authorize from `sub`.
2. Observe for 20 minutes (15-minute access-token TTL plus five-minute clock
   skew). Revert issuance to legacy `sub` if the operational error gate fails;
   resource services still consume the explicit claims.
3. **Only now run the formal final gate, owned by the user:** the selected
   production-like test suite and the attach-only live Compose verifier. No
   agent/operator runs these commands automatically.
4. Retain dual claims, additive columns, events, receipts and compatibility
   reads for at least 30 days after that gate. A separate approved contraction
   release may remove them; it is not part of this fast rollout.

## Required Platform Controls

| Control | Why it is needed | Owner |
| --- | --- | --- |
| Immutable image digest + per-service rollout revision | Makes every flag change attributable and reversible. | Platform/SRE |
| Auth registration allowlist/percentage cohort | Avoids a 0→100% traffic jump in R3. | Auth |
| Per-topic consumer lag, retry and owner-scoped DLT alert | Distinguishes a healthy Pod from a healthy event flow. | Platform/SRE |
| Outbox pending/oldest-age and relay-outcome alert | Detects a stalled producer even when Kafka consumer lag is zero. | Service owners |
| Principal-fallback dashboard by service/surface | Supplies the evidence for each R4 ownership promotion. | Service owners |
| Runbook with named on-call and stop/rollback decision | Makes a release safe under incident pressure. | Engineering/SRE |

## Risks And Recovery

- A new account can stall between Auth and User: stop intake, retain and replay
  the User outbox; do not create a second identity or restore a synchronous
  registration RPC.
- At-least-once delivery can duplicate an event: inbox receipts, event IDs and
  lifecycle versions make replay safe; conflicting reuse or version gaps go to
  retry/DLT and are investigated before promotion.
- A pre-profile block can be missed by an absent projection: Auth re-emits its
  authoritative blocked snapshot when the profile links.
- A migration projection can be incomplete: enforcement remains disabled until
  projection parity is proven; unknown mappings fail closed only after the
  seed/remediation gate.
- Kubernetes ConfigMap changes do not restart Pods: change the owning pod
  template revision or restart only that Deployment, then verify its consumer
  group assignment.

## Progress

- [x] C1: Auth returns the canonical `principalId` plus the opaque recovery
  handle, expiry and lifecycle status; User's response correctly reports that
  Auth linkage is asynchronous.
- [x] C2: Flutter's wire DTO and domain `RegistrationResult` retain
  `principalId`, `registrationHandle`, `expiresAt` and `lifecycleStatus` while
  immediately invoking User registration.
- [x] C3/C4: If User returns an error or times out after Auth succeeded, the
  repository calls the Auth handle-status endpoint once. Auth returns an
  explicit `profileLinked` fact, so the client never infers completion from a
  lifecycle action (an account can be blocked before profile creation). A
  missing/false linkage yields an incomplete result and keeps the form open.
  There is no automatic polling and no persistence of a provisioning JWT or
  handle. Recovery after app restart is an idempotent re-submit using the same
  credentials, which causes Auth to mint a fresh handoff for the existing
  `PENDING_PROFILE` identity.
- [x] C5: The refreshed code-only audit (`2026-08-14`) reports all 15
  resource services using strict explicit JWT claims and no resource boundary
  reading access-token `sub`.
- [x] Shipper → Tracking → Delivery code path closeout: Delivery now injects
  the local shipper-principal projection in its production constructor, while
  retaining the direct-fixture constructor. When projection enforcement is
  enabled, a missing repository fails closed instead of silently falling back
  to the legacy shipper ID. Delivery/Tracking/Shipper modules compile with
  tests skipped after this fix.
- [x] Make Shipper identity bootstrap outbox insertion atomic with
  `ON CONFLICT (event_type, aggregate_id) DO NOTHING`, so concurrent relay
  instances cannot turn the legacy-row seed check into a transaction failure.
- [ ] C6: Final acceptance remains owned by the user and is intentionally last.
- [x] Add principal/lifecycle/outbox/inbox foundations, dual JWT claims and
  JWKS resource verification.
- [x] Implement password provisioning through signed handoff and profile
  outbox; Auth consumes `identity.profile.created`.
- [x] Implement Auth lifecycle events and User/Shipper projections with
  replay/DLT handling.
- [x] Add additive ownership migrations, principal-first paths, metrics and
  service-scoped Kubernetes flags for the currently scoped services.
- [x] Document the detailed flag order, Compose intent and final verifier.
- [x] Add Auth-owned registration admission: fail-closed master switch,
  private allowlist, deterministic keyed percentage cohort and non-PII
  admission metrics. Kubernetes delivers the optional allowlist/HMAC secret to
  Auth only and rejects partial percentage rollout without its key.
- [x] Add a Kubernetes identity rollout runner with R1/R2/R3 owner-scoped
  changes, ConfigMap/deployment preflight, explicit mutation/parity
  confirmations, and reverse-order rollback commands. It does not apply a
  whole overlay or substitute runtime metric gates.
- [x] Align rollout-runner phases with the fast plan: R2 has independent User
  and Shipper consumer commands; R3 is registration admission; R4 is Shipper
  ownership mapping. Deprecated combined/old phase names remain aliases so an
  already-prepared operator command is not broken.
- [x] Add bounded retention cleanup for opaque Auth registration handles and
  make the Kafka resilience provisioner/verifier own all five identity
  retry/DLT topologies. Source topics and ACLs remain a Kafka platform/GitOps
  prerequisite; the application never depends on broker auto-create at a
  feature-flag cutover.
- [x] Add dormant Auth-only R5 **access-token** `sub` issuer mode
  (`LEGACY_USER_ID` → `PRINCIPAL_ID`), guarded rollout/rollback commands and
  Kubernetes wiring. Refresh tokens intentionally retain legacy `sub` through
  their seven-day lifetime.
  Tracking WebSocket now requires explicit dual claims and never treats `sub`
  as a legacy profile ID, so it remains correct through the subject switch.
- [x] Complete the Restaurant → Order R4 ownership cell: service-scoped
  default-off enforcement flags, measured dual reads, fail-closed principal
  authorization when enabled, Kubernetes wiring, guarded forward promotion and
  reverse-order rollback. Other R4 business cells remain independently staged.
- [x] Complete independent Notification inbox and Settlement refund-history R4
  cells: default-off principal-only reads, local fallback metrics, Kubernetes
  wiring, explicit per-cell parity confirmations and scoped rollback. Historical
  immutable rows are never guessed or rewritten from another service database.
- [x] Complete Promotion wallet and Flash Sale reservation R4 cells:
  service-scoped default-off strict ownership flags, dual-mode fallback
  metrics, principal-only wallet reads/locks or principal-required reservation
  writes, Kubernetes wiring, per-cell parity confirmation and scoped rollback.
- [x] Replace the final Compose identity verifier stub with an explicit,
  attach-only live proof for registration handoff, outbox/inbox idempotency,
  User/Shipper lifecycle projection and identity-DLT cleanliness. It remains
  user-owned and cannot execute without the final-gate environment variables,
  ADMIN token file and a disposable linked shipper principal.
- [x] Remove the unreachable User → Auth registration HTTP client, its
  Internal-Token DTO/config/test surface and unused RestTemplate bean. Public
  provisioning now has one executable User boundary: local JWKS verification
  followed by User outbox publication.
- [x] Remove the corresponding unreachable Auth internal resolve/complete
  endpoints and opaque `USER_PROVISIONING` token rail. Password reset and email
  verification token persistence remain unchanged; the legacy enum value stays
  readable until retention drain, but no producer/consumer remains. Public
  registration rollback is intake closure plus durable event convergence, not a
  synchronous RPC.
- [x] Add event-native legacy bootstrap rather than cross-database principal
  updates: User relay idempotently seeds profile-created for every local
  principal/profile mapping; Auth status bootstrap emits one snapshot per
  existing linked account behind its own flag/receipt and pending gauges.
- [x] Align the executable Kubernetes rollout guard with the fast-track
  release policy: T0 preflight validates every flagged R4 Deployment; R5
  requires an explicit HTTP/WebSocket claim-consumption audit plus healthy
  Track A signals, rather than incorrectly waiting for all service-local R4
  fallback drains. Manifest verification asserts every identity flag enters
  exactly its owning Deployment.
- [x] Add a static R5 explicit-claim audit: it inventories every service
  depending on the shared resource-server starter, requires strict converter
  wiring, rejects direct resource reads of access-token `sub`, and checks the
  Tracking WebSocket and Gateway header-stripping boundaries.
- [x] Make the R5 runner enforce the fast-track dependency state itself:
  successful T1 profile/lifecycle path and completed 100% R3 admission are
  required before the Auth-only issuer change; R4 strict flags are explicitly
  not dependencies.
- [x] Make Kubernetes preflight verify target-cluster Deployment env bindings
  for every identity control before a shared ConfigMap is mutated. This closes
  the private-overlay drift gap: local generated-manifest checks cannot prove
  what the reconciled cluster actually injects.
- [x] Make Auth/User provisioning reject identity-key divergence: legacy
  `users.auth_id` must equal `users.principal_id` (both `auth_account.id`), and
  the signed provisioning handoff requires `principal_id` rather than falling
  back to the mutable R5 `sub` compatibility field.
- [x] Add the Auth/User key invariant to the static R5 audit so a future
  compatibility edit cannot silently reintroduce a provisioning-`sub` fallback
  or a divergent `authId`/`principalId` profile link.
- [x] Correct the rollout-runner sequencing defect: R3 admission now requires
  the complete R1/R2 profile and lifecycle plumbing rather than opening while
  the Auth status relay is deliberately disabled. Static manifest verification
  guards that prerequisite set.
- [x] Enforce the User-local identity invariant at database level: Flyway V3
  fails T0 with an explicit remediation error when `users.auth_id` diverges
  from `users.principal_id`, otherwise installs a PostgreSQL check constraint.
- [x] Canonicalize Auth email identity (`trim + lowercase`) in service lookup
  and a PostgreSQL unique expression index. Auth Flyway V8 stops T0 for
  case/whitespace-only historical collisions rather than selecting an account.
- [x] Extend the static identity audit with the canonical-email query/index
  invariant, so a future Auth compatibility change cannot return to a
  case-sensitive account lookup while the migration is active.
- [x] Enforce `identity_claims_version=1` at the shared HTTP converter and the
  Tracking WebSocket handshake, and add it to the static audit. Access-token
  boundaries now reject missing or unknown identity-claim shapes before R5.
- [x] Make the two new T0 identity constraints production-safe: User adds and
  validates its check constraint through PostgreSQL `NOT VALID`, while Auth V8
  builds the canonical-email unique index concurrently outside Flyway's
  transaction to avoid a long registration write lock.
- [x] Make Auth V8 recover from an interrupted concurrent-index build by
  detecting and removing only its known invalid index artifact before retrying.
- [x] Prove the local Compose T0/T1 wiring can start without weakening the
  final user-owned test gate. On 2026-08-14, local PostgreSQL occupied host
  `5432`, so the isolated Compose database was published at `55432` while
  service-to-service traffic retained `postgres:5432`. After mounting the
  operator-owned JWT secret override, Auth/User/Gateway all reported
  readiness `UP`; Flyway applied Auth V5–V8 and User V2–V3; Auth served an
  RS256 JWK with `kid=auth-key-1`; and the Gateway JWKS route returned `200`.
  Two runtime constructor ambiguities were fixed before that evidence:
  `UserServiceImpl` and `IdentityRegistrationService` now explicitly select
  their production constructors while retaining direct-fixture constructors.
- [x] Correct the public User-registration response wording: it now says
  identity linkage is processing instead of claiming the asynchronous Auth
  consumer has already linked the profile. The client-facing source of truth
  remains the opaque Auth registration-handle status endpoint.
- [x] Expose the canonical `principalId` in the public Auth registration
  response alongside legacy `authId`, and teach the Flutter registration DTO
  to retain it. Existing clients remain compatible; new code no longer has to
  infer the canonical identity key from a legacy-named field.
- [ ] Execute T0–T2 with production-like runtime evidence.
- [ ] Open password registration by R3 cohort and record each promotion gate.
- [ ] Complete remaining R4 service-cell backfills/remediation.
- [ ] Execute the R5 subject cutover, then run the final user-owned test/E2E
  gate and schedule the 30-day cleanup release. R4 ownership cells continue
  independently and do not serially block this compatibility cutover.

## Decisions

- 2026-08-14: `principalId` is `auth_account.id`; profile and domain IDs retain
  their business meanings.
- 2026-08-14: Access and refresh tokens carry explicit principal and legacy
  claims. Resource services never infer principal identity from legacy `sub`.
- 2026-08-14: Auth owns lifecycle; outbox + Kafka is the production
  coordination path. The legacy scheduler is an event-off rollback rail only.
- 2026-08-14: Incremental implementation/release preparation uses
  compile/static checks only; the user owns the sole formal test, Compose and
  E2E gate after JWT subject cutover.
- 2026-08-14: Registration cohort gate is implemented as a private email
  allowlist plus HMAC-stable percentage (0–100). Product/SRE must approve its
  concrete audience, promotion holds and SLO before any non-zero production
  cohort is enabled.
- 2026-08-14: Fast-track release policy separates the Auth/JWKS platform from
  service-local ownership remediation. `sub` moves only after explicit-claim
  compatibility is audited and Track A is healthy; it does not wait for every
  R4 strict flag. R4 retains per-service parity and fallback gates.

## Validation

During implementation and release preparation (no test execution):

- Changed-module `mvn -DskipTests compile`, Flyway/contract inspection,
  generated-manifest checks (including
  `backend_delivery/scripts/verify-identity-explicit-claims.sh`) and
  `git diff --check` only when relevant files change.
- No unit, integration, Compose, Kafka or E2E command is run automatically.

Current code-only validation (2026-08-14):

- Flutter Freezed/JSON/Retrofit and Mockito outputs were regenerated after the
  registration-result contract change.
- The complete Flutter Auth test-source tree statically analyses after the
  repository return-type change; stale Mockito mocks for login/refresh were
  regenerated to the new `RegistrationResult` signature.
- FVM Dart static analysis passed for the affected Auth source and contract,
  datasource, repository, use-case and notifier fixture files.
- Auth's registration-status contract now returns `profileLinked`; the
  `auth-service` reactor compile with tests skipped passed after this additive
  field, and Flutter treats an absent/false field as incomplete (fail closed).
- The full backend Maven reactor (25 modules) compiled with `-DskipTests`
  after the Shipper outbox and Delivery projection fixes.
- `backend_delivery/scripts/verify-identity-explicit-claims.sh` passed:
  15 resource services use strict explicit claims and none authorizes from
  access-token `sub`.

Local runtime evidence (2026-08-14, not a formal acceptance test):

- `POSTGRES_HOST_PORT=55432 docker compose -f docker-compose.yml -f docker-compose.secrets.yml up -d ...`
  used existing operator-owned key files without exposing their values.
- `auth-service`, `user-service` and `api-gateway` health/readiness were
  observed as `UP`; Auth's direct JWKS endpoint and the public Gateway JWKS
  route both returned the same RS256 public JWK with HTTP `200`.
- This evidence proves service startup, additive migration application and
  JWKS publication/routing only. It deliberately does **not** prove a public
  registration, Auth→User→Auth convergence, lifecycle projection or DLT
  cleanliness; those remain within the final user-owned acceptance gate.

User-owned final gate, after R5 subject cutover:

- Run `backend_delivery/scripts/verify-identity-principal-migration.sh` and,
  when the topology/fixtures are ready,
  `IDENTITY_LIVE_E2E=true bash backend_delivery/scripts/verify-identity-principal-migration.sh`.
- Verify registration recovery/replay/expiry, lifecycle status propagation,
  ownership authorization paths, DLT/retry behaviour, rollback and the JWT
  subject switch against a production-like Kafka/PostgreSQL environment.

## Result

Not complete until runtime promotion gates and the user-owned final test gate
have succeeded. The source is ready for additive rollout; it is not evidence
that JWKS, Kafka projections or principal ownership have been proven live.
