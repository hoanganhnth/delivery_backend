# Source Map, Freshness and Coverage

> Use this map to resolve a question back to the strongest available evidence.
> It prevents a reader from treating a historical plan, a disabled controller or
> a local Compose observation as a universal production fact.

## Evidence levels

| Label | Meaning |
| --- | --- |
| `Executable authority` | Current code, tests, migrations, manifests/configuration or observed runtime script result. |
| `As-built guide` | This system documentation, derived from executable authority on the stated date. |
| `Runbook/decision` | Approved/maintained operational or architecture policy; verify environment-specific facts before execution. |
| `Historical` | Context and prior work only; do not use as current status without cross-check. |
| `Decision required` | A future choice explicitly not yet approved. |

## Current source hierarchy

1. Current source/config/tests/migrations in the owning repository.
2. `backend_delivery/docs/system-contract-inventory.md` for service/topic/state
   inventory and `backend_delivery/docs/http-api-inventory.md` for handler paths.
3. Backend ADRs/runbooks/workflow docs and root ADRs.
4. `backend_delivery/docs/platform/system/` for cross-cutting reconstruction
   and operations navigation.
5. Plans only for progress/evidence history. Historical baseline documents must
   not override newer code or accepted ADRs.

## Coverage matrix

| Subject | Canonical guide | Detailed source / executable authority | Freshness/status |
| --- | --- | --- | --- |
| Whole-system diagrams/client boundary | [architecture.md](../architecture.md), [diagram standards](../diagram-standards.md) | [`architecture.md`](../architecture.md), [`product/overview.md`](../../product/overview.md) | As-built, 2026-08-24 |
| Service ownership/ports/capabilities | [service-catalog.md](../service-catalog.md) | [`backend_delivery/docs/system-contract-inventory.md`](../../../system-contract-inventory.md), Compose | As-built, 2026-08-09 |
| Exact HTTP handlers and signature/DTO source map | [api/README.md](../api/README.md), [generated API catalog](../api/http-contract-catalog.md), [`http-contract.json`](../api/http-contract.json) | [`backend_delivery/docs/http-api-inventory.md`](../../../http-api-inventory.md), controller DTO/tests | 220 controller operations and 190 reachable source schemas in deterministic JSON/Markdown artifacts; not OpenAPI |
| HTTP formatting/pagination/money/time | [api/README.md](../api/README.md) | [`docs/decisions/0001-backend-contract-conventions.md`](../../decisions/0001-backend-contract-conventions.md) | Accepted convention |
| JWKS/auth/security | [security.md](../security.md) | [`backend ADR 0001`](../../../decisions/0001-jwks-resource-server-authentication.md), Auth/Gateway config/tests | Accepted/as-built, 2026-08-09 |
| Registration and COD/domain flows | [workflows.md](../workflows.md) | backend `docs/workflows/`, service tests, runtime scripts | As-built, 2026-08-09; proof varies by flow |
| Kafka/retry/DLT/state machines | [events-and-data.md](../events-and-data.md) | system contract inventory, Kafka config/tests/runbooks | As-built; legacy normalization remains incomplete |
| DB/storage/migration/recovery | [events-and-data.md](../events-and-data.md) | Flyway folders, backup/recovery scripts/runbook | As-built locally; production data plane decision required |
| Client architecture/contracts | [clients.md](../clients.md) | client source, READMEs, action-contract/client tests | As-built; native device proof remains limited |
| SDKs, direct dependency roles, container images and CI tooling | [technology-and-tooling.md](../technology-and-tooling.md) | backend POM/Docker/Compose, client manifests, Gradle wrappers and CI workflows | As-built inventory; source manifests pin exact versions |
| Local runtime | [operations/README.md](../operations/README.md) | Compose/Dockerfile/scripts | Runtime verified locally/staging only |
| Production deployment | [deployment-foundation.md](../operations/deployment-foundation.md) | [Kubernetes foundation](../../../../deploy/kubernetes/README.md), backend rollout/secrets/backup runbooks | Renderable provider-neutral base; requirements/decision required, not deployed |
| Observability | [observability.md](../operations/observability.md) | Prometheus/Grafana/OTel config and runbooks | Instrumented locally; retention/on-call/SLO decision required |
| Simulator | [simulator/README.md](../simulator/README.md) | `backend_delivery/simulator-service`, `delivery_simulator_web`, runner tests | MVP runner/console partial; Kafka/DB observer and durable storage open |
| Offline reference snapshot | [reference/README.md](../reference/README.md) | `sync-reference-bundle.mjs` manifest/checksum mirror | Generated; must be refreshed with source docs/config |

## Documents that need careful interpretation

| Material | How to use it |
| --- | --- |
| `backend_delivery/SYSTEM_REVIEW.md` | Explicitly stale per backend `AGENTS.md`; do not use for current status. |
| `docs/plans/active/priority-roadmap.md` | Valuable historical/evidence log, but its opening section flags pre-JWKS Gateway identity notes as superseded. |
| Completed plans | Evidence/recovery history; current source/config/ADRs decide whether a fact still holds. |
| Disabled payment/promotion/flash-sale/analytics/livestream source | Implementation material only, not public MVP authorization. |
| Local Docker Compose | Developer/rehearsal topology; never proof of production HA, cloud secrets, traffic or DR. |

## Source update checklist

For every architecture-relevant change, use this matrix:

| Change | Must update/check |
| --- | --- |
| HTTP path/actor/DTO | Controller/test, HTTP inventory, refresh with `generate-http-contract.mjs --write` then prove with `--check`, API guide, client call sites/action checks |
| Kafka event/state transition | Producer/consumer test, event inventory, workflow, DLT/replay/migration plan |
| Database schema/ownership | Flyway, service docs, data map, backup/recovery compatibility |
| Identity/key/internal security | Auth/Gateway/resource test, security guide, secret/rollout runbook |
| Deploy/health/config/secret | Manifest/Compose/runbook, operations guide, readiness and rollback proof |
| Client/public capability | Client docs/tests plus feature status; verify Gateway route/service flag and ownership |

## Verification commands and evidence sources

The exact command set evolves; prefer scripts in `backend_delivery/scripts/`.
Representative current checks include:

```text
scripts/verify-build-baseline.sh
scripts/verify-http-api-inventory.sh
node docs/platform/system/api/generate-http-contract.mjs --check
scripts/verify-compose-config.sh
scripts/verify-kubernetes-manifests.sh
scripts/rollout-kubernetes.sh
scripts/verify-docker-artifact-freshness.sh
scripts/verify-docker-runtime-security.sh
scripts/verify-runtime-startup.sh
node docs/platform/system/rebuild/sync-reference-bundle.mjs --check
scripts/verify-mvp-cod-flow.sh
scripts/verify-settlement-crash-window.sh
scripts/verify-backup-restore-rehearsal.sh
scripts/verify-secrets.sh
```

Treat a command as proof only after reading what it actually validates and
whether it ran against an isolated/local/staging/production environment.
