# Execution Plan: System Reconstruction Documentation and Production Foundation

Date: 2026-08-08

## Status

Active

## Outcome

Create one canonical, navigable documentation set that lets a new engineer or
agent understand and recreate the current Delivery platform without treating
historical notes as current truth. Then turn the verified deployment gaps into
an executable, production-first implementation plan. The later end-to-end
scenario simulator is designed only after this foundation is complete.

## Context

- The workspace is a polyrepo: backend, customer Flutter app, React web portal,
  and React Native shipper app.
- Current structural truth is distributed across `docs/`, `backend_delivery/docs/`,
  code, Compose configuration, tests, and runtime/runbook evidence.
- `docs/ARCHITECTURE.md` and the backend contract inventories already contain
  valuable source material, but the navigation and freshness boundary are not
  sufficient for a clean-room rebuild.
- The JWKS migration has been rolled out and verified in local/staging Compose;
  this does not constitute production rollout evidence.

## Scope

In scope:

- A canonical documentation folder under root `docs/` with an explicit source
  hierarchy, reconstruction path, system maps, service/API/event/data/security/
  client/operations material, and editable Mermaid diagrams.
- Clear separation of verified current behavior, intentionally hidden
  capabilities, historical material, target-state recommendations, and open
  operator/product decisions.
- Deployment-first plan and implementation work that is supported by existing
  repository authority.
- A detailed design and delivery plan for an observable end-to-end simulator;
  implementation follows the documentation/deployment foundation.

Out of scope for the first documentation slice:

- Inventing cloud provider, production domain, secret-manager, payment-provider,
  RPO/RTO, SLO, retention, or monetary-policy values that are not yet approved.
- Calling a Compose rollout proof a production deployment.
- Opening hidden payment, promotion, flash-sale, analytics, or livestream APIs.
- Implementing the simulator before its system contracts and deployment surface
  have been documented.

## Approach

1. Inventory current code, configuration, contracts, runbooks, clients and
   diagrams; identify authoritative versus historical material.
2. Establish `docs/system/` as the canonical reconstruction entry point. Keep
   detailed service-local material where it belongs and link to it with a
   freshness/status label rather than duplicating unchecked facts.
3. Write the deployment and operations baseline first: environment topology,
   image/config/secret boundaries, database/Kafka lifecycle, observability,
   rollout/recovery, and the decisions that block concrete Kubernetes manifests.
4. Fill in architecture, service catalog, client/API/event/data/security and
   domain workflow documents from inventories and source.
5. Verify links, source references, diagrams and terminology; update the root
   documentation map and correct stale JWKS navigation where evidence supports
   it.
6. Produce the simulator design, scenarios, observability model and phased
   implementation plan. Implement only after it has a bounded contract and the
   deployment foundation has progressed.

## Risks And Recovery

- Documentation can drift from executable truth. Each canonical document must
  state its verification date and direct source links; code/config/tests remain
  authoritative when they disagree.
- Large scope can create a duplicate document forest. `docs/system/README.md`
  will be the single navigation root and refer to service-local details rather
  than copy them blindly.
- Production topology choices need owner approval. Mark them `Decision required`
  and do not encode them as enabled manifests or runtime policy.
- Changes are documentation/additive planning only at first and can be reverted
  as normal Git changes. Later rollout work must include explicit rollback and
  data-recovery proof.

## Progress

- [x] Read root/backend workflow and identify existing documentation sources.
- [x] Build a source/coverage inventory and establish the canonical structure.
- [x] Write deployment-first reconstruction documentation.
- [x] Write architecture, service, contract, workflow, data, client and security documentation.
- [x] Validate links, source coverage and diagram syntax; update navigation/stale references.
- [x] Create a checksum-backed offline reference bundle containing first-party
  docs, API inventories, runbooks, migrations, deployment/configuration and
  client manifests under `docs/system/reference/`.
- [x] Create a bounded simulator design and its implementation plan.
- [x] Add a guarded, wave-ordered Kubernetes rollout operator script; leave it
  unapplied until an approved overlay and cluster context exist.
- [x] Implement and validate a provider-neutral Kubernetes foundation without inventing production endpoints, secrets or data-plane policy.
- [x] Prove dependency-aware local Compose startup for the 13-service COD core,
  observability wiring and the volume-preserving Search recovery path.
- [x] Health-gate Compose application startup on Config Server and Eureka
  readiness, and enforce that contract in the Compose verifier.
- [x] Make disabled local capabilities opt-in through a Compose profile and
  give the shared application readiness healthcheck a cold-start budget proven
  on the constrained Docker Desktop runtime.
- [x] Align the backend local runbook, Docker guide and clean-room rebuild guide
  with the core/profile startup path so a fresh rebuild has one safe command
  sequence.
- [x] Add a deterministic, source-derived HTTP contract manifest for all 166
  controller operations, including Java parameter bindings and reachable DTO
  field declarations; keep it checked against the controller/inventory source
  and mirrored in the offline reference bundle.
- [x] Add a deterministic, human-readable API catalog generated from that same
  contract, so every operation and reachable DTO/enum can be read/navigated
  without parsing JSON.
- [x] Add a technology/tooling inventory that links the backend and client
  manifests, exact local image tags, CI toolchains and reconstruction rules.
- [x] Replace the clean E2E downtime design with a disposable Compose project,
  run-scoped PostgreSQL/Kafka volumes and a dynamic loopback Gateway port; keep
  the full double-stack run pending until sufficient Docker capacity exists.
- [x] Propagate the disposable Compose file/project into the seed, COD and
  failure-matrix children, including all PostgreSQL/Kafka fixture commands, and
  add a static regression gate against accidental canonical-stack `exec` use.
- [x] Harden the guarded Kubernetes rollout against `NodePort` as well as
  `LoadBalancer` service exposure, and validate that private-service invariant
  in the manifest verifier.
- [ ] Obtain owner approval for the production platform decision packet (P1–P9).
- [ ] Implement approved deployment foundation changes with behavior-appropriate proof.
- [ ] Implement the simulator after its plan and authorities are complete.

## Decisions

- 2026-08-08: `docs/system/` is the proposed canonical navigation layer. It
  documents the as-built system and links to executable/source-local authority;
  it does not replace code, tests, or environment-specific operator settings.
- 2026-08-08: Deployment documentation may define safe, provider-neutral
  requirements and decision gates now. Concrete production infrastructure is
  deferred until the owner selects the unresolved provider and operational
  policies.
- 2026-08-08: Add a generated Kustomize base for all 17 application services
  plus Config/Eureka, but deliberately retain invalid endpoint/image values and
  omit Ingress, NetworkPolicy, data plane and plaintext Secret resources. This
  makes the foundation renderable/CI-verifiable without suggesting it is safe to
  apply to production.
- 2026-08-08: Treat documentation repository ownership as decision P9 because
  the workspace root is intentionally not a Git repository. Do not initialize a
  new repository or move files without the owner's explicit choice.

## Validation

- Focused proof: `node docs/system/verify-docs.mjs` passed on 2026-08-08:
  18 Markdown documents, 12 Mermaid blocks, 17 service catalog entries and all
  local Markdown targets resolved; it also invokes the checksum verifier for the
  offline reference bundle. `sync-reference-bundle.mjs --check` passed with 413
  first-party documentation/configuration/migration/deployment/client-manifest
  source files mirrored under `docs/system/reference/`.
- HTTP reconstruction contract proof (2026-08-08):
  `node docs/system/api/generate-http-contract.mjs --check` passed with 166
  controller mappings across 14 controller-owning services and 136 reachable
  source-declared DTO schemas. `bash
  backend_delivery/scripts/verify-http-api-inventory.sh` independently confirmed
  the 166 route count against Java mapping annotations. The generated manifest is
  explicitly a source map, not an invented OpenAPI document; `node
  docs/system/rebuild/sync-reference-bundle.mjs --check` then passed with 415
  mirrored first-party source documents/configuration assets.
- Human-readable API catalog proof (2026-08-08): the same deterministic
  generator now emits `docs/system/api/http-contract-catalog.md`, grouping all
  166 controller operations by service and rendering Java source links,
  parameter bindings/defaults/validation/signatures plus 136 reachable source
  schemas. It is checked together with the JSON manifest and remains a source
  map, not invented OpenAPI.
- Technology/tooling inventory proof (2026-08-08):
  `docs/system/technology-and-tooling.md` links direct backend/client manifests,
  CI workflows, Gradle wrappers, Dockerfile/Compose images and explains their
  supported versus local-only role. It is a required system document and is
  included in the generated offline reference bundle.
- Kubernetes foundation proof: `bash
  backend_delivery/scripts/verify-kubernetes-manifests.sh` passed. It confirms
  58 generated manifest files are current and base/staging template render into
  19 private workloads with probes/non-root/read-only filesystem, no Ingress,
  LoadBalancer/NodePort service or plaintext Secret. The backend CI now runs the same verifier
  after installing a pinned `kubectl` renderer.
- Kubernetes rollout safety proof (2026-08-08): the guarded rollout now rejects
  both `LoadBalancer` and `NodePort` service types rather than the ineffective
  `kind: NodePort` check. Shell parsing, a positive NodePort-regex probe,
  `verify-kubernetes-manifests.sh`, and the no-input rollout refusal all passed;
  no cluster/API call was made.
- Container proof: `docker build --build-arg SERVICE_PATH=api-gateway` completed
  for the updated shared Dockerfile. Image metadata reports `USER 10001:10001`;
  the retained `backend_delivery/scripts/verify-docker-runtime-security.sh`
  then confirms the JAR is readable and `/tmp` is writable as the non-root user
  under a read-only root filesystem. `verify-docker-artifact-freshness.sh` also
  passed after the Dockerfile change; both checks are now backend CI steps after
  Maven packaging.
- Rollout safety proof: the guarded operator script rejects the unconfigured
  placeholder staging overlay before contacting a cluster; no Kubernetes API
  server/context is configured in this workspace, so no resource was applied.
  A direct `kubectl config get-contexts`/`cluster-info` check on 2026-08-08
  confirmed that no current context or reachable API server exists.
- Local runtime checkpoint (2026-08-08): Docker Desktop reported
  `elasticsearch` `OOMKilled=true`/exit `137` while all JVM services were running.
  Stopping only the disabled capability services, restarting the existing
  Elasticsearch container without rebuilding or recreating it, and then starting
  `search-service` restored both Docker healthchecks; the Gateway search smoke
  returned HTTP `200` with an empty projection result. This evidence confirms a
  local memory-pressure failure, not loss of the existing container state. The repeatable recovery
  is [`backend_delivery/scripts/recover-local-search.sh`](../../../../scripts/recover-local-search.sh)
  and its [runbook](../../../runbooks/local-runtime-search-recovery.md).
- Build/runtime checkpoint (2026-08-08): explicit Homebrew JDK 17 was used for
  `mvn -B -DskipTests package` and `mvn -B test`; the reactor package and full
  test suite completed with zero failures/errors (833 tests, 7 existing skips).
  The revised startup proof deliberately starts the 13-service COD core and
  leaves the four disabled capabilities stopped. `STARTUP_TIMEOUT_SECONDS=420
  bash scripts/verify-runtime-startup.sh` passed after starting control plane →
  data plane → observability → Auth → resource services → Gateway; it preserved
  the canonical PostgreSQL/Kafka volumes and proved both Gateway public read
  routes. `bash scripts/verify-observability-runtime.sh` then confirmed
  Prometheus readiness, Grafana dashboard provisioning and all core scrape
  targets. `bash scripts/recover-local-search.sh` passed afterwards, starting
  only the existing Elasticsearch/Search containers and returning Gateway
  search HTTP 200 without recreating data volumes.
- Compose dependency hardening proof (2026-08-08): `docker compose
  -f docker-compose.yml -f docker-compose.secrets.yml config --quiet` and
  `bash scripts/verify-compose-config.sh` passed after every application
  workload was given healthy Config Server/Eureka prerequisites. A fresh
  `STARTUP_TIMEOUT_SECONDS=420 bash scripts/verify-runtime-startup.sh` then
  passed with those dependency gates active; the runtime log observed both
  control-plane containers healthy before resource services started.
- Compose runtime safety proof (2026-08-08): the default Compose profile now
  excludes `promotion-service`, `flashsale-service`, `analytics-service` and
  `livestream-service`; the verifier renders that default separately and then
  validates all four with `COMPOSE_PROFILES=optional-capabilities`. The shared
  image readiness healthcheck uses a 120-second cold-start grace period plus 12
  15-second attempts. A fresh 13-service `STARTUP_TIMEOUT_SECONDS=480`
  startup passed after a previous cold run had demonstrated that the old
  30-second/5-attempt budget could mark a still-booting Restaurant service
  unhealthy. `verify-actuator-config.sh`, `verify-compose-config.sh`,
  `verify-docker-runtime-security.sh` and the Prometheus/Grafana runtime
  verifier all passed afterwards.
- Startup/re-registration proof (2026-08-08): the startup verifier now requires
  an actual Eureka `UP` lease after each service's Actuator readiness and only
  restarts the affected service if that lease never converges. It uses
  `--no-deps` for Auth/resource/Gateway waves so they cannot recreate an already
  healthy Config/Eureka pair. `RUNTIME_REBUILD_IMAGES=false` passed against the
  live canonical stack with all Compose container IDs unchanged and both public
  Gateway reads returning `200`; `RUNTIME_REBUILD_IMAGES=true` remained the
  explicit release/isolated-E2E path and passed the ordered full startup smoke.
- Repository gate proof (2026-08-08): an ambient JDK 25 invocation was rejected
  as expected; rerunning `bash scripts/verify-build-baseline.sh` with the
  documented JDK 17 `JAVA_HOME` passed, including the 166-handler HTTP inventory,
  Actuator configuration and Spring Boot/Cloud version baseline checks.
- Integration proof: source/config cross-checks covered the service module list,
  HTTP inventory, system contract inventory, Compose topology, backend/client
  READMEs, operations runbooks, ADRs and completed JWKS rollout record.
- Full isolated COD/WebSocket/settlement E2E remains an explicit pending gate.
  `verify-mvp-cod-flow.sh` creates orders and ledger rows, while
  `verify-clean-compose-e2e.sh` now uses a separate Compose project and
  disposable volumes without stopping, recreating or mounting the canonical
  project. Its config-only safety check and isolated Redis probe passed; the
  full second-stack rehearsal remains deferred because this Docker Desktop host
  has insufficient headroom alongside the canonical core.
- Isolated-child propagation proof (2026-08-08): `seed.sh`,
  `verify-mvp-cod-flow.sh` and `verify-mvp-failure-matrix.sh` now honor an
  inherited `COMPOSE_FILE` rather than rebuilding a canonical Compose command;
  all direct PostgreSQL/Kafka `exec` calls use the resulting command wrapper.
  `bash -n` passed for the four affected scripts, the JDK-17 build-baseline
  gate passed, `verify-compose-config.sh` passed, and
  `CLEAN_E2E_CONFIG_ONLY=true bash scripts/verify-clean-compose-e2e.sh` passed.
  This proves source/config isolation only; the capacity-limited full second
  stack rehearsal remains pending.
- Capacity preflight (2026-08-08): Docker Desktop exposed 7.75 GiB while the
  running 22-container canonical core consumed roughly 6.2 GiB. A concurrent
  clean 13-service core would risk OOM, so it was intentionally not started or
  used to disturb the canonical stack. Run the full proof on a dedicated
  sufficiently provisioned runner or after an explicit Docker-memory increase.
- Documentation freshness: `docs/README.md` now points at `docs/system/`; the
  stale `docs/ARCHITECTURE.md` link to an active JWKS plan now points at the
  completed Compose migration record and explicitly distinguishes it from a
  production rollout.
- Runtime proof: only local/staging Compose evidence is claimed. Later
  deployment implementation must add environment-specific proof. A local
  `kubectl apply --dry-run=client` cannot schema-validate without a reachable
  Kubernetes API server, so cluster admission/network/secret/data-plane proof
  remains intentionally unclaimed.

## Deferred implementation handoff

This plan is intentionally paused at the production-authority boundary. The
documentation corpus and provider-neutral deployment foundation are usable now;
they are **not** an approved production environment. Do not create a cloud
account, public DNS endpoint, secret, cluster, managed data service or payment
integration from this plan alone.

### Preconditions to resume deployment

1. Complete and approve P1–P9 in
   [`docs/system/operations/production-platform-decision-packet.md`](../../system/operations/production-platform-decision-packet.md).
   In particular, P1–P8 define the platform/security/data/reliability policy
   and P9 gives `docs/system/` a version-controlled owner.
2. Obtain an approved cluster context, private environment overlay location and
   operator access. Do not place provider credentials, JWT keys or database
   passwords in this workspace.
3. Reserve a dedicated CI/runner or increase Docker Desktop memory before the
   full clean Compose E2E. The current workstation cannot safely run a second
   13-service core beside the 22-container canonical stack.

### Resume sequence

1. Copy the relevant private overlay template and replace only the approved
   image digests, private endpoints, namespace, workload identity and external
   secret references. Render and policy-scan it; preserve the Gateway-only
   public edge.
2. Provision the selected PostgreSQL/Kafka/Redis/search topology, least-
   privilege roles/ACLs, PITR/backup retention and migration controller from
   P3/P7. Provision secrets through the P4 mechanism and prove missing-secret
   startup failure.
3. Provision metrics/logs/traces/alerts and on-call routing from P6, then set
   measured requests/limits, replicas, PDB/HPA and canary thresholds from P8.
4. Run `bash backend_delivery/scripts/verify-kubernetes-manifests.sh`, render
   the private overlay, then use the guarded rollout command with an explicit
   context/namespace confirmation. Roll out control plane → Auth → resource
   services → Gateway; for JWKS migration include the approved token-TTL plus
   clock-skew wait.
5. Prove Gateway TLS/CORS/trusted-proxy behavior, JWKS public shape/rotation,
   COD/notification/WebSocket flow, Kafka retry/DLT/restart, backup restore and
   rollback in that environment. Record actual evidence in this plan before
   declaring a staging or production release.

### Simulator remains a later phase

Do not start the multi-actor simulator UI before the deployment preconditions
above are satisfied. Its design already lives at
[`docs/system/simulator/README.md`](../../system/simulator/README.md): it will
drive a customer, restaurant and configurable nearby shippers through the real
Gateway contracts and display order/Saga/match/delivery/settlement/trace state.
When deployment is ready, create a separate implementation plan from that
design, choose the simulator host/auth boundary and test against an isolated
environment rather than a developer's canonical data.

## Result

Complete after the canonical corpus, deployment implementation, and simulator
have each met their stated verification requirements. Record verified outcome,
remaining decision gates and follow-up before moving this plan to completed.
