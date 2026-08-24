# Production Deployment Foundation

> Status: provider-neutral blueprint. It specifies the minimum deployment
> outcome and evidence required to move beyond Compose; it intentionally does
> not choose a cloud provider, public domain, secret product or data-plane
> operator without owner approval.

## Outcome to implement

A release must be capable of deploying an immutable version of the delivery
platform to a private, highly available environment where:

- only the Gateway is reachable from client traffic;
- every workload receives the correct non-secret configuration and least-
  privilege secret/data access;
- traffic flows only to readiness-healthy instances;
- database/event state survives node/instance loss according to approved RPO/RTO;
- metrics, logs and traces let an operator determine where a COD flow failed;
- a canary can be stopped and rolled back without corrupting state.

## Reference workload layout

| Layer | Controller shape | Minimum production properties |
| --- | --- | --- |
| Gateway | horizontally replicated Deployment + public ingress | TLS, WAF/rate-limit upstream policy, readiness routing, graceful WebSocket drain, no direct service exposure |
| Stateless/resource services | replicated Deployment | private service, probes, config/secret mounts, resource limit, PDB, controlled consumer shutdown |
| Auth | replicated Deployment with exclusive private signing-key read | key rotation procedure, public JWKS route, readiness and audit; do not mount key to Gateway/resource services |
| Config/Eureka | HA private controllers or approved replacement | multi-failure-domain replicas, immutable config label, backup/recovery and access control |
| OTel/metrics | collector/Prometheus/Grafana operator or managed service | private scrape, authenticated operator UI, durable trace/log export and retention policy |
| PostgreSQL | managed HA service or dedicated operator | per-service DB/role, WAL/PITR, encrypted backup, restore test, upgrade/runbook |
| Kafka | managed cluster or dedicated operator | replication/min ISR, topic provisioning, ACLs, retry/DLT retention, consumer lag monitoring |
| Redis | managed HA service or dedicated operator | TLS/auth/ACL where supported; loss behavior documented because state is volatile |
| Search | managed/operated search cluster | version compatibility, snapshot/rebuild and projection recovery plan |

## Workload contract

### Build and image promotion

1. Build every Maven module from a clean, pinned toolchain and run its focused
   tests plus repository gates.
2. Build a container from the exact artifact; the existing Dockerfile rejects a
   stale JAR. Generate an SBOM/provenance and scan the final image.
3. Push a content-addressable digest to the approved registry; promote the same
   digest from development to staging to production. Do not rebuild under the
   same tag in each environment.
4. Attach release metadata: Git revision, image digest, config label, migration
   version range and secret version identifiers.

### Configuration and secret injection

- Config Server receives non-secret config only. The deployment selects a
  protected immutable label/commit.
- Secret values enter only through the approved workload identity/secret
  mechanism as mounted files or a secure runtime interface. Startup fails if a
  required secret is absent.
- Per-service database roles, Kafka ACLs and secret access rules must be
  explicit. A shared superuser credential is not a production baseline.
- A key/credential rotation is a release workflow with overlap, readiness and
  rollback evidence, not an in-place environment edit.

### Health and traffic

| Probe | Meaning | Traffic decision |
| --- | --- | --- |
| Startup | process completed potentially slow bootstrap/config/migration dependency initialization | Prevent premature liveness restarts |
| Liveness | process can continue running | Restart only |
| Readiness | required dependencies/config/registration are available for this workload | Add/remove from Service/Ingress traffic |

Use `/actuator/health/readiness` on management port 9090 for the current
application contract. Do not route through Gateway to test a service's
management endpoint and do not expose that port through public Ingress.

### Networking

Start with default-deny NetworkPolicy. Allow only:

- Ingress Controller/WAF → Gateway application port.
- Gateway → approved resource-service ports and WebSocket upstream.
- Services → Config/Eureka, owned database, Kafka, Redis/Search as applicable,
  OTel collector/DNS and exact internal HTTP targets.
- Prometheus → private management port.
- Operator backup/restore tooling → approved data-plane endpoints.

All other client-to-service, service-to-unowned-DB, and public-to-management
paths must be denied. Test policy with an in-cluster probe pod before opening
traffic.

## Data-plane production checklist

### PostgreSQL

- Database/role per service, TLS, encryption, backups and privileges.
- Base backup + continuous WAL archive to encrypted, access-controlled storage.
- Point-in-time restore, isolated reconciliation and recorded RTO rehearsal.
- Migration job/ordering that is backward compatible and avoids two app pods
  racing DDL.

### Kafka

- Explicit topic creation, partition plan, replication factor/min ISR and ACLs.
- Retry/DLT topic retention (current operator baseline uses 14 days) and an
  owner/replay runbook.
- Monitoring for broker health, partition under-replication, consumer lag, DLT
  growth and unavailable producer/consumer groups.
- A tested broker/consumer rolling upgrade and recovery procedure.

### Redis and search

- Redis failure may alter cache/rate-limit/realtime behavior; availability mode
  and data loss behavior must be tested, not assumed.
- Search projection is rebuildable from durable sources; document version
  compatibility/snapshot policy before large-scale use. The local 7.17 server
  and Boot-managed 8.x client need a deliberate upgrade compatibility roadmap.

## Staged implementation plan

| Phase | Deliverables | Evidence gate |
| --- | --- | --- |
| D0 — decisions | Approved provider/region/domain/data/secret/SLO/RPO-RTO table | Decision record with accountable owner and no ambiguous defaults |
| D1 — repo delivery | Image build/push, SBOM/scan, digest promotion, environment config/secret interface, manifest/Helm/Kustomize layout | Rendered manifests, policy scan, secret absence test and CI artefacts |
| D2 — shared platform | Private network, registry, ingress, identity, data plane, observability services | Access/network probes and provider backup/monitoring configuration evidence |
| D3 — staging | Deploy Auth/control plane then resources/Gateway; configure test clients | Readiness, JWKS/public-route, COD smoke, trace/metric/log correlation |
| D4 — resilience | Canary, PDB/HPA baseline, node/pod/dependency failure, DLT/replay and PITR restore rehearsal | Measured results against approved SLO/RPO/RTO |
| D5 — production | Controlled traffic ramp and on-call/runbook handoff | Release record, error/lag/latency gates and rollback rehearsal |

## Why no generic production manifests are committed yet

Kubernetes YAML without image registry, namespace policy, domain/TLS, secret
reference implementation, storage classes, data-plane endpoints and RPO/RTO is
not safely deployable. It risks encoding arbitrary values as security/operations
policy. After D0, create `backend_delivery/deploy/` (or a separately approved
infrastructure repository) with a base plus environment overlays and validate it
against the selected platform.

Implementation update 2026-08-08: a renderable, intentionally non-apply-ready
Kustomize foundation now lives at
[`backend_delivery/deploy/kubernetes/`](../../../../deploy/kubernetes).
It contains all current backend workloads, private services, probes, projected
secret-file contracts, non-root hardening and template overlays. Its invalid
endpoint/image placeholders, absent Ingress/NetworkPolicy/data-plane manifests
and verifier are deliberate guardrails; D0 is still required before creating an
environment overlay or claiming a Kubernetes rollout. The guarded
`backend_delivery/scripts/rollout-kubernetes.sh` now encodes control → Auth →
resources → Gateway sequencing, optional JWKS TTL/skew wait, public-JWKS shape
check and placeholder/secret/public-service refusal; it has not been applied to
a cluster.

## Inputs required from the owner

1. Where production/staging run: provider/on-prem, region(s), account/project
   boundary and budget range.
2. Public domain/DNS/TLS/WAF owner and expected traffic/geographies.
3. Managed versus self-managed PostgreSQL/Kafka/Redis/search and who operates
   each one.
4. Preferred secret manager/workload identity integration.
5. Production RPO/RTO, data retention/compliance and initial SLO/canary error
   budget targets.

Use the Vietnamese [production platform decision packet](./production-platform-decision-packet.md)
to record/approve these inputs without putting any secret values in Git.

Until these are answered, we can safely implement repository-independent image
quality and documentation but cannot honestly say a Kubernetes deployment is
implemented.
