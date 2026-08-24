# Identity Principal Event Rollout

## Preconditions

- Deploy the additive Flyway migrations before enabling any identity flag.
- Before R1, have the Kafka platform/GitOps manifest create the three source
  topics (`identity.profile.created`, `identity.status.changed`,
  `shipper.identity.upserted`) with their approved partitions and ACLs. Then
  run `scripts/provision-kafka-resilience-topics.sh` to reconcile their five
  owner-scoped retry/DLT topologies; it defaults
  `PROVISION_IDENTITY_RETRY_TOPICS=true`. Do not rely on Kafka auto-topic
  creation when a migration flag is first enabled.
- All affected resource services must accept explicit dual identity claims:
  `principal_id` and `legacy_user_id`. Access-token `sub` remains legacy until
  the separately guarded R5 subject-cutover section below.
- Preserve the legacy Auth → User **status-sync** configuration only for a
  status rollback. Public password registration has no synchronous Auth/User
  fallback and must remain closed until the durable profile-event path is
  healthy; do not describe or restore a registration RPC as a rollback rail.
- In Kubernetes, patch the service-specific keys in `delivery-runtime`, not a
  shared `IDENTITY_EVENTS_ENABLED` value. The generated Deployments map these
  keys explicitly, so an Auth rollout cannot activate User/Shipper consumers:
  `AUTH_IDENTITY_EVENTS_ENABLED`, `AUTH_IDENTITY_OUTBOX_RELAY_ENABLED`,
  `AUTH_IDENTITY_STATUS_BOOTSTRAP_ENABLED`,
  `AUTH_PUBLIC_REGISTRATION_ENABLED`, `AUTH_PUBLIC_REGISTRATION_CANARY_PERCENTAGE`,
  `AUTH_JWT_ACCESS_TOKEN_SUBJECT_MODE`,
  `USER_IDENTITY_EVENTS_ENABLED`, `USER_IDENTITY_OUTBOX_RELAY_ENABLED`,
  `SHIPPER_IDENTITY_EVENTS_ENABLED`, `SHIPPER_IDENTITY_OUTBOX_RELAY_ENABLED`,
  `DELIVERY_SHIPPER_IDENTITY_PROJECTION_ENFORCED`, and
  `TRACKING_SHIPPER_IDENTITY_PROJECTION_ENFORCED`,
  `RESTAURANT_PRINCIPAL_OWNERSHIP_ENFORCED`, and
  `ORDER_PRINCIPAL_OWNERSHIP_ENFORCED`,
  `NOTIFICATION_PRINCIPAL_OWNERSHIP_ENFORCED`, and
  `SETTLEMENT_PRINCIPAL_OWNERSHIP_ENFORCED`,
  `PROMOTION_PRINCIPAL_OWNERSHIP_ENFORCED`, and
  `FLASHSALE_PRINCIPAL_OWNERSHIP_ENFORCED`.
- A ConfigMap update does not restart Pods. Every private-overlay change to one
  of these keys must change the owning Deployment's pod-template annotation
  (for example, a reviewed immutable `delivery.platform/config-revision`) or
  be followed by `kubectl rollout restart deployment/<owner>`. Wait for that
  one Deployment before inspecting its consumer group. Never restart all
  resource services merely to enable an identity flag.
- Use `scripts/rollout-identity-principal-kubernetes.sh` for a narrow,
  imperative owner restart only when GitOps is not applying the same reviewed
  overlay change. It defaults to read-only `preflight`/`status` and refuses
  mutation without `CONFIRM_IDENTITY_ROLLOUT=YES`. Make the value durable in
  the private overlay first; otherwise reconciliation can undo the wave. Its
  preflight reads the target Deployment specs and verifies every application
  environment variable points to its expected `delivery-runtime` key; a local
  generated manifest alone is not accepted as proof of the live overlay.

## Kubernetes flag ownership

| Capability | Runtime key | Only Deployment restarted |
| --- | --- | --- |
| Auth consume profile event | `AUTH_IDENTITY_EVENTS_ENABLED` | `auth-service` |
| Auth relay identity outbox | `AUTH_IDENTITY_OUTBOX_RELAY_ENABLED` | `auth-service` |
| Auth bootstrap existing linked lifecycle snapshots | `AUTH_IDENTITY_STATUS_BOOTSTRAP_ENABLED` | `auth-service` |
| Open public password registration | `AUTH_PUBLIC_REGISTRATION_ENABLED` | `auth-service` |
| Registration canary percentage | `AUTH_PUBLIC_REGISTRATION_CANARY_PERCENTAGE` | `auth-service` |
| JWT `sub` issuer mode | `AUTH_JWT_ACCESS_TOKEN_SUBJECT_MODE` | `auth-service` |
| User consume status event | `USER_IDENTITY_EVENTS_ENABLED` | `user-service` |
| User relay profile-created outbox | `USER_IDENTITY_OUTBOX_RELAY_ENABLED` | `user-service` |
| Shipper consume status event | `SHIPPER_IDENTITY_EVENTS_ENABLED` | `shipper-service` |
| Shipper relay identity mapping | `SHIPPER_IDENTITY_OUTBOX_RELAY_ENABLED` | `shipper-service` |
| Tracking fail-closed mapping | `TRACKING_SHIPPER_IDENTITY_PROJECTION_ENFORCED` | `tracking-service` |
| Delivery fail-closed mapping | `DELIVERY_SHIPPER_IDENTITY_PROJECTION_ENFORCED` | `delivery-service` |
| Restaurant fail-closed owner authorization | `RESTAURANT_PRINCIPAL_OWNERSHIP_ENFORCED` | `restaurant-service` |
| Order fail-closed customer/owner authorization | `ORDER_PRINCIPAL_OWNERSHIP_ENFORCED` | `order-service` |
| Notification fail-closed inbox authorization | `NOTIFICATION_PRINCIPAL_OWNERSHIP_ENFORCED` | `notification-service` |
| Settlement fail-closed refund-history authorization | `SETTLEMENT_PRINCIPAL_OWNERSHIP_ENFORCED` | `settlement-service` |
| Promotion fail-closed wallet ownership | `PROMOTION_PRINCIPAL_OWNERSHIP_ENFORCED` | `promotion-service` |
| Flash Sale fail-closed reservation ownership | `FLASHSALE_PRINCIPAL_OWNERSHIP_ENFORCED` | `flashsale-service` |

## Fast production rollout model

Operate this rollout as two independent tracks, not one long global critical
path:

| Track | Release train | Promotion rule |
| --- | --- | --- |
| Identity platform | T0 additive release → T1 profile/lifecycle pipes → T2 registration and Auth-only `sub` issuer cutover | Outbox age, bootstrap drain, consumer lag/retry/DLT, admission/HTTP error deltas and explicit-claim compatibility are clean. |
| Service ownership | T3 individual R4 cells | The owning service has local parity, no legacy fallback for seven days, no relevant lag/retry/DLT and a clean client-error gate. |

Resource services authorize from `principal_id` and `legacy_user_id`; access
token `sub` is only a compatibility field. Consequently the R5 Auth issuer
cutover does **not** wait for all R4 cells. It does wait for a recorded audit
that every deployed HTTP and WebSocket boundary reads explicit claims, plus a
healthy identity-platform signal set. Roll back only Auth token issuance if
the 20-minute observation fails.

Run `bash scripts/verify-identity-explicit-claims.sh` against the exact release
source/image revision before recording that audit. It inventories every Maven
module using the shared resource-server starter, requires strict converter
wiring, checks the Tracking WebSocket handshake, and rejects a resource-service
read of access-token `sub`. It also requires provisioning handoff to use
`principal_id` and verifies User rejects a divergent compatibility `authId`.
It is a static compatibility check; it does not replace the runtime Track A
metric gate.

For speed, use the smallest useful registration sequence: private allowlist
only (0%), 5%, 25%, then 100%. HMAC cohort assignment is stable across retry.
At each hold, inspect operational signals; no unit, integration, Compose,
Kafka or E2E suite is run by the rollout operator. The single formal
production-like/E2E and attach-only Compose gate remains user-owned and occurs
after R5.

## Wave 1: consumer-safe deployment

Deploy every service with its service-owned migration flag false. In a
Kubernetes overlay, this means:

```text
AUTH_IDENTITY_EVENTS_ENABLED=false
AUTH_IDENTITY_OUTBOX_RELAY_ENABLED=false
AUTH_IDENTITY_STATUS_BOOTSTRAP_ENABLED=false
AUTH_PUBLIC_REGISTRATION_ENABLED=false
AUTH_PUBLIC_REGISTRATION_CANARY_PERCENTAGE=0
AUTH_JWT_ACCESS_TOKEN_SUBJECT_MODE=LEGACY_USER_ID
USER_IDENTITY_EVENTS_ENABLED=false
USER_IDENTITY_OUTBOX_RELAY_ENABLED=false
SHIPPER_IDENTITY_EVENTS_ENABLED=false
SHIPPER_IDENTITY_OUTBOX_RELAY_ENABLED=false
DELIVERY_SHIPPER_IDENTITY_PROJECTION_ENFORCED=false
TRACKING_SHIPPER_IDENTITY_PROJECTION_ENFORCED=false
RESTAURANT_PRINCIPAL_OWNERSHIP_ENFORCED=false
ORDER_PRINCIPAL_OWNERSHIP_ENFORCED=false
NOTIFICATION_PRINCIPAL_OWNERSHIP_ENFORCED=false
SETTLEMENT_PRINCIPAL_OWNERSHIP_ENFORCED=false
PROMOTION_PRINCIPAL_OWNERSHIP_ENFORCED=false
FLASHSALE_PRINCIPAL_OWNERSHIP_ENFORCED=false
```

This applies schema and code only. Public password registration returns 503
before creating an Auth identity; this is intentional because the deployed
public contract has no synchronous Auth→User completion fallback. Existing
login and block sync retain their current behavior.

The User Flyway migration also enforces `users.auth_id = users.principal_id`.
It fails rather than guessing if local rows diverge; remediate such rows in the
User database before retrying T0. This is a single-service identity invariant,
not a cross-database backfill.

Auth Flyway V8 likewise installs a canonical-email unique index on
`lower(trim(email))` concurrently, avoiding a long registration-table lock. It
stops T0 if two historic Auth accounts differ only by case or surrounding
whitespace. Resolve those duplicate identities through the Auth remediation
process before rerunning the migration; never delete or merge them ad hoc
during a traffic rollout. If a concurrent build is cancelled, V8 removes only
its own invalid index artifact and retries safely on the next Flyway run.

## Wave 2: profile event path

1. Deploy Auth with `AUTH_IDENTITY_EVENTS_ENABLED=true` and keep
   `AUTH_PUBLIC_REGISTRATION_ENABLED=false`; it begins consuming
   `identity.profile.created`. Public admission stays closed until the full
   lifecycle path in Wave 3 is also running.
2. Deploy User with `USER_IDENTITY_OUTBOX_RELAY_ENABLED=true` and leave
   `USER_IDENTITY_EVENTS_ENABLED=false` until status projection is enabled.
3. Confirm the User relay has an empty/healthy outbox and Auth's consumer group
   is assigned. Deliver the Auth-only `delivery-auth-registration-canary`
   secret (keys: `allowlist`, `hash-key`) before opening the master admission
   gate at any 0–99% cohort; never place those values in the ConfigMap.
4. Do not open `AUTH_PUBLIC_REGISTRATION_ENABLED` in this wave. The T2
   admission command follows Wave 3 after status consumers, Auth bootstrap and
   the Auth relay are all healthy.

Rollback: registration is still closed. Keep the Auth consumer and User relay
alive unless the deployment itself is unsafe; historical profile events remain
safe in the durable outbox. Public rollback behavior is described under T2
admission below; there is no legacy synchronous link fallback for this public
contract.

Local Compose is deliberately different from the Kubernetes Wave-1 template:
its defaults enable the complete Auth/User profile flow so local registration
does not manufacture unlinked identities, and enable Auth status bootstrap so
persisted local profiles converge too. Override its `AUTH_*`/`USER_*`
environment variables only when rehearsing a particular rollout wave.

### Kubernetes operator commands

These commands do not replace the runtime evidence gates in this runbook. They
only patch `delivery-runtime`, annotate, and wait for the *owning* Deployment.
They never run tests, create client traffic, query Kafka/Prometheus, or apply a
whole overlay. Make the exact ConfigMap value durable in the private GitOps
overlay first, then use an approved target:

```bash
export KUBE_CONTEXT=approved-staging
export KUBE_NAMESPACE=delivery-staging

# Read-only R0 preflight.
bash scripts/rollout-identity-principal-kubernetes.sh preflight

# R1: exactly one capability/owner at a time.
CONFIRM_IDENTITY_ROLLOUT=YES \
  bash scripts/rollout-identity-principal-kubernetes.sh r1-auth-consumer
CONFIRM_IDENTITY_ROLLOUT=YES \
  bash scripts/rollout-identity-principal-kubernetes.sh r1-user-relay

# R2: one status consumer cell at a time, then drain the Auth status bootstrap
# before enabling the relay.
CONFIRM_IDENTITY_ROLLOUT=YES \
  bash scripts/rollout-identity-principal-kubernetes.sh r2-user-consumer
# Inspect User group assignment/lag/retry/DLT before continuing.
CONFIRM_IDENTITY_ROLLOUT=YES \
  bash scripts/rollout-identity-principal-kubernetes.sh r2-shipper-consumer
# Inspect Shipper group assignment/lag/retry/DLT before continuing.
CONFIRM_IDENTITY_ROLLOUT=YES \
  bash scripts/rollout-identity-principal-kubernetes.sh r2-status-bootstrap
CONFIRM_IDENTITY_ROLLOUT=YES \
  bash scripts/rollout-identity-principal-kubernetes.sh r2-auth-relay

# T2 registration comes only after the complete T1 profile/lifecycle path is
# running. Stop and inspect outbox age, consumer lag/retry/DLT and HTTP errors
# at every hold. Any cohort below 100% needs the Auth-only Secret (allowlist +
# hash-key).
CONFIRM_IDENTITY_ROLLOUT=YES IDENTITY_REGISTRATION_CANARY_PERCENTAGE=0 \
  bash scripts/rollout-identity-principal-kubernetes.sh r3-admission
CONFIRM_IDENTITY_ROLLOUT=YES IDENTITY_REGISTRATION_CANARY_PERCENTAGE=5 \
  bash scripts/rollout-identity-principal-kubernetes.sh r3-admission
```

R3 is traffic admission: use `r3-admission` with cohort `0`, then `5`, `25`,
and `100`; stop at every cohort gate. R4 is the Shipper mapping
cell: use `r4-shipper-relay`, record projection-count parity and zero
lag/retry/DLT, then invoke `r4-tracking-enforce` and `r4-delivery-enforce` with
`CONFIRM_SHIPPER_PROJECTION_PARITY=YES`. The former `r3-*` mapping command names
remain compatibility aliases only. `close-registration` stops percentage intake;
`IDENTITY_CLOSE_ALLOWLIST=true` also stops the allowlist. `rollback-status`
stops only the R2 relay. `rollback-profile` is the guarded full R1/R2 fallback;
it requires `CONFIRM_REGISTRATION_DRAINED=YES` after the outbox/consumer gate.
`rollback-shipper` and both status commands retain all additive recovery data.

Restaurant/Order are a separate R4 ownership cell. Each service dual-reads
principal plus unbackfilled legacy IDs while its own flag is false, and records
`delivery_identity_legacy_fallback_total`. Backfill only locally provable
mappings, remediate the remainder, then enforce in this order:

```bash
CONFIRM_IDENTITY_ROLLOUT=YES \
CONFIRM_RESTAURANT_PRINCIPAL_PARITY=YES \
  bash scripts/rollout-identity-principal-kubernetes.sh r4-restaurant-enforce

CONFIRM_IDENTITY_ROLLOUT=YES \
CONFIRM_ORDER_PRINCIPAL_PARITY=YES \
  bash scripts/rollout-identity-principal-kubernetes.sh r4-order-enforce
```

Order enforcement requires Restaurant enforcement. Both flags fail closed for
unbackfilled records; use `rollback-restaurant-order` in reverse order if the
cell's post-enforcement error gate fails. Do not disable the R1/R2 identity
event path or modify another service cell.

Notification inbox and Settlement refund history are independent read-only R4
cells. They never infer or lazy-write an immutable historical principal: repair
only locally proven rows, then enable one service at a time after seven days of
zero fallback and its own client-error gate:

```bash
CONFIRM_IDENTITY_ROLLOUT=YES \
CONFIRM_NOTIFICATION_PRINCIPAL_PARITY=YES \
  bash scripts/rollout-identity-principal-kubernetes.sh r4-notification-enforce

CONFIRM_IDENTITY_ROLLOUT=YES \
CONFIRM_SETTLEMENT_PRINCIPAL_PARITY=YES \
  bash scripts/rollout-identity-principal-kubernetes.sh r4-settlement-enforce
```

Use `rollback-notification` or `rollback-settlement` only for the failing
cell. Enforcement hides records without a principal rather than granting
legacy-ID access, so remediation remains visible and fail-closed.

Promotion wallet and Flash Sale reservation are write-path R4 cells. In dual
mode their fallback metric records historical wallet rows without a principal
or incoming Flash Sale reservation requests without `userPrincipalId`. After
local remediation and seven days zero fallback, activate each independently:

```bash
CONFIRM_IDENTITY_ROLLOUT=YES \
CONFIRM_PROMOTION_PRINCIPAL_PARITY=YES \
  bash scripts/rollout-identity-principal-kubernetes.sh r4-promotion-enforce

CONFIRM_IDENTITY_ROLLOUT=YES \
CONFIRM_FLASHSALE_PRINCIPAL_PARITY=YES \
  bash scripts/rollout-identity-principal-kubernetes.sh r4-flashsale-enforce
```

Strict Promotion mode only reads/locks `user_principal_id` wallets; strict
Flash Sale mode rejects an incoming reservation without `userPrincipalId`.
Use `rollback-promotion` or `rollback-flashsale` only for the affected cell.

## R5 JWT subject cutover

`sub` is an Auth **access-token** issuer compatibility field, not an
authorization identity: all resource HTTP and WebSocket boundaries must use the
required explicit `principal_id` and `legacy_user_id` claims. After the
identity-platform gate (healthy profile/lifecycle outbox, zero identity
lag/retry/DLT and the recorded explicit-claim boundary audit), make the
Auth-only setting durable in GitOps and run:

```bash
CONFIRM_IDENTITY_ROLLOUT=YES \
CONFIRM_SUBJECT_CUTOVER=YES \
CONFIRM_EXPLICIT_CLAIM_AUDIT=YES \
  bash scripts/rollout-identity-principal-kubernetes.sh r5-subject-principal
```

The R5 runner executes the static audit itself and rejects any configuration
that has not completed T1 plus 100% registration admission. It deliberately
does not inspect or require an R4 ownership flag: those cell-local migrations
remain independently reversible.

Observe HTTP/WebSocket error and JWT claims-version metrics for 20 minutes
(15-minute access-token TTL + five-minute clock skew). Within that window,
`rollback-subject-legacy` changes only the Auth access-token issuer mode back
to `LEGACY_USER_ID`; refresh tokens deliberately retain their legacy `sub` in
both modes and resource services keep their explicit-claim behavior.

### Final Compose proof (user-owned)

The final verifier attaches to an existing Compose stack; it never invokes
`up`, `down`, build or destructive cleanup. It creates a retained unique USER
fixture, requires an existing ADMIN token to prove block/unblock, and checks
the Auth/User database link, outbox publish state, inbox dedup replay,
block/unblock projection and empty identity DLT topics. Run it only after the
full final test gate is authorized:

```bash
IDENTITY_LIVE_E2E=true \
IDENTITY_E2E_PASSWORD='unique-operator-password' \
IDENTITY_E2E_ADMIN_ACCESS_TOKEN_FILE=/secure/admin.access.jwt \
IDENTITY_E2E_SHIPPER_PRINCIPAL_ID=123 \
bash scripts/verify-identity-principal-compose.sh
```

It rejects any Compose flag state other than the coherent full Auth/User event
path. The shipper principal must be an existing disposable, active, linked
fixture: the verifier block/unblocks it to prove the Shipper lifecycle
projection, so use an offline fixture. It does not print tokens, passwords or
provisioning payloads.

## Wave 3: status projection path

1. Wave 2 User relay also seeds `identity.profile.created` for every existing
   User row with a non-null local `principal_id`, in idempotent batches. Before
   status rollout, wait until
   `delivery_identity_bootstrap_pending{owner="user",event="profile_created"}`
   is zero, Auth profile consumer lag/DLT is clean, and linked-profile count
   has converged. Do not cross-join databases or infer a principal from a
   domain ID.
2. Deploy User and Shipper with `USER_IDENTITY_EVENTS_ENABLED=true` and
   `SHIPPER_IDENTITY_EVENTS_ENABLED=true` first. Keep
   `AUTH_IDENTITY_OUTBOX_RELAY_ENABLED=false` while their groups join.
3. Confirm both consumer groups are assigned and their lag is zero. Enable
   `AUTH_IDENTITY_STATUS_BOOTSTRAP_ENABLED=true` on Auth. It writes exactly one
   `identity.status.changed` snapshot per existing linked Auth account, using a
   local Auth bootstrap receipt. Wait for
   `delivery_identity_bootstrap_pending{owner="auth",event="status_changed"}`
   to reach zero, then confirm Auth outbox age/lag/DLT are clean before any
   historical status outbox row is released.
4. Restart Auth with `AUTH_IDENTITY_OUTBOX_RELAY_ENABLED=true` (it already has
   `AUTH_IDENTITY_EVENTS_ENABLED=true` from Wave 2). This drains any status
   rows created while a profile completed in Wave 2 and subsequently publishes
   block/unblock changes. Auth does not call the legacy Auth-to-User status HTTP
   scheduler in this mode; it neither creates new legacy pending rows nor
   processes retained legacy pending rows while the event flag is enabled.
   Auth also re-emits a `BLOCKED` lifecycle snapshot when it links a profile,
   so an administrative block that occurred before profile creation cannot be
   ACKed by an absent projection and then leave the new profile active.
   A projection at lifecycle version `0` treats its first status event as an
   authoritative bootstrap snapshot even when the version is higher than one;
   strict version-gap DLT behavior starts after that first snapshot. This covers
   block/unblock history that predates a newly-created profile.
5. T2 may now set `AUTH_PUBLIC_REGISTRATION_ENABLED=true` with cohort `0`,
   then advance `5%`, `25%`, and `100%`. At every hold inspect admission and
   provisioning latency, Auth/User outbox age, consumer lag/retry/DLT and HTTP
   error deltas. A partial percentage requires the Auth-only allowlist/HMAC
   secret. An unhealthy gate returns cohort percentage to `0`; do not restore
   a synchronous Auth/User registration RPC.
6. Block and unblock a disposable account; verify User and Shipper projection
   versions, active/blocked state, session revocation and DLT/lag. Registration
   recovery is covered by the user-owned final acceptance gate.

Rollback: `rollback-status` first stops only `AUTH_IDENTITY_OUTBOX_RELAY_ENABLED`;
the healthy R1 profile path remains on, so public registration cannot create an
unlinked identity. A complete return to the legacy HTTP rail must first close
registration, wait until every accepted registration drains through the User
outbox/Auth consumer with zero lag/DLT, then use `rollback-profile` with
`CONFIRM_REGISTRATION_DRAINED=YES`. That command disables Auth event mode before
the status consumers and User relay. Do not drop event/outbox/inbox tables.

## Subject cutover gate

After T1/R3 identity-platform signals are healthy and the explicit-claim audit
covers every deployed HTTP/WebSocket resource boundary, Auth may change
access-token `sub` from legacy profile ID to `principalId`. It does not wait
for R4 principal-ownership cells: those services authorize using explicit
claims, not `sub`. Require the normal identity DLT/lag/error gate and observe
for 20 minutes (15-minute access TTL plus five-minute clock skew). Roll back
only the Auth issuer mode if it fails. R4 cells still require seven days of
zero local fallback before their own strict mode.

## Required dashboards

- Identity outbox pending count and oldest age. Prometheus metric names are
  `delivery_identity_outbox_pending` and
  `delivery_identity_outbox_oldest_age_seconds`, tagged by `owner=auth|user|shipper`.
  Relay outcome is `delivery_identity_outbox_relay_total{outcome}`. A non-zero
  or increasing oldest age is a rollout stop condition; do not advance on a
  merely empty ready batch.
- Bootstrap pending count is `delivery_identity_bootstrap_pending{owner,event}`.
  It must reach zero before the corresponding relay/enforcement gate; it is not
  acceptable to rely on a one-off cross-service SQL backfill.
- Consumer lag/retry/DLT by `identity.profile.created` and
  `identity.status.changed`.
- Lifecycle transition count by source/status.
- Legacy ownership fallback and JWT claims-version counts.
  Instrumented ownership surfaces include Promotion wallet, Order customer/
  restaurant-owner authorization, Delivery customer/restaurant-owner and
  pre-enforcement shipper mapping, Tracking pre-enforcement shipper mapping,
  Notification inbox, and Settlement customer refund history. They use
  `delivery_identity_legacy_fallback_total{service,surface}`.
  Its rate must be zero for seven days before any principal-only cutover that
  includes the wallet.
- Per-consumer retry/DLT counts for `identity.profile.created`,
  `identity.status.changed`, and `shipper.identity.upserted`. Identity retry
  destinations are owner-scoped (`*.auth-identity.DLT`, `*.user-identity.DLT`,
  `*.shipper-identity.DLT`, `*.delivery-shipper-identity.DLT`,
  `*.tracking-shipper-identity.DLT`); no identity DLT may be ignored during a
  cutover gate.

## Shipper domain-ID projection wave

`shipper.id` is a fulfilment aggregate ID and must never be assumed equal to
JWT `sub`, `legacy_user_id`, or `principal_id`.

1. Deploy Shipper, Delivery and Tracking migrations/consumers with
   `SHIPPER_IDENTITY_OUTBOX_RELAY_ENABLED=false`,
   `TRACKING_SHIPPER_IDENTITY_PROJECTION_ENFORCED=false`, and
   `DELIVERY_SHIPPER_IDENTITY_PROJECTION_ENFORCED=false`.
2. Deploy Shipper with `SHIPPER_IDENTITY_OUTBOX_RELAY_ENABLED=true`. Its relay
   gradually seeds every existing linked (`principal_id` non-null) shipper row,
   then publishes newly created profiles transactionally.
3. Wait for Delivery/Tracking consumer lag to reach zero; inspect retry/DLT and
   compare projection row count with Shipper linked-row count. Resolve any
   existing rows missing `principal_id` before enforcement.
4. Enable `TRACKING_SHIPPER_IDENTITY_PROJECTION_ENFORCED=true` in Tracking
   first, then `DELIVERY_SHIPPER_IDENTITY_PROJECTION_ENFORCED=true` in
   Delivery. REST and WebSocket shipper actions now resolve principal to local
   `shipper.id` and fail closed if the projection is not ready.

Rollback: turn enforcement off first, then stop the relay if necessary. Keep
the topic, outbox, inbox and projection tables; they are additive recovery data.
