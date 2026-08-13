# Delivery Kubernetes Foundation

> Status: provider-neutral, renderable **staging/production template**. It is
> deliberately not an apply-ready production environment: image registry,
> private endpoints, secret provider, DNS/TLS, NetworkPolicy egress, capacity,
> SLO and data-plane choices must be supplied by an approved private overlay.

## What this package provides

- Kustomize base for all 17 backend application services plus Config Server and
  Eureka Discovery Server (19 workloads total).
- One private `ClusterIP` Service per workload. There is no Ingress,
  LoadBalancer, NodePort or plaintext `Secret` in the base.
- A single public-edge rule by construction: only a future, explicitly approved
  Gateway Ingress template may target `api-gateway`.
- Actuator `startupProbe`, readiness and liveness probes on private management
  port `9090`; rolling updates use readiness rather than process liveness as the
  traffic gate.
- Non-root container runtime, read-only root filesystem, dropped Linux
  capabilities, RuntimeDefault seccomp profile, bounded `/tmp`, separate service
  accounts and no automatic service-account token mount.
- Per-service projected secret files, matching the existing Spring
  `configtree:/run/secrets/` convention; no secret is placed in environment
  variables, ConfigMaps, image layers or this repository.
- Default-off values for payment/refund, promotion/flash-sale checkout,
  analytics and livestream capabilities, preserving the current COD MVP
  boundary.

The generator is the editable workload inventory. It writes committed resource
files under `base/generated/` so `kubectl kustomize` can render without Node.
Do not hand-edit generated files.

## Render and verify

From `backend_delivery/`:

```bash
node deploy/kubernetes/generate.mjs --check
bash scripts/verify-kubernetes-manifests.sh
kubectl kustomize deploy/kubernetes/base
kubectl kustomize deploy/kubernetes/overlays/staging-template
```

After changing the service inventory or generator:

```bash
node deploy/kubernetes/generate.mjs --write
bash scripts/verify-kubernetes-manifests.sh
```

The verifier intentionally checks that template endpoints/images remain
`*.example.invalid` / `registry.example.invalid`, so a template cannot be
mistaken for an approved release. It does **not** call `kubectl apply` or need a
cluster. CI also runs `scripts/verify-docker-artifact-freshness.sh` and
`scripts/verify-docker-runtime-security.sh` after packaging to prove that an
actual service image rejects stale artifacts and runs as the same non-root user
required by the Kubernetes pod security context.

## Workload inventory

| Group | Workloads |
| --- | --- |
| Control plane | `config-server`, `discovery-server` |
| Public edge | `api-gateway` only |
| Identity/catalog/order | `auth-service`, `user-service`, `restaurant-service`, `order-service` |
| Fulfilment/realtime | `delivery-service`, `match-service`, `shipper-service`, `tracking-service`, `notification-service`, `saga-orchestrator-service` |
| Search/finance/support | `search-service`, `settlement-service`, `promotion-service`, `flashsale-service`, `analytics-service`, `livestream-service` |

Every service remains private. `match-service` and
`saga-orchestrator-service` have Services for private health/operations and
logical DNS only; that does not make an HTTP business API public.

## Configuration contract

`base/runtime-config.yaml` contains only non-secret values. Its endpoint and
origin fields intentionally use invalid placeholder hostnames. A real overlay
must patch at least:

- `SPRING_KAFKA_BOOTSTRAP_SERVERS`, Redis, Elasticsearch and OTLP endpoints.
- Config Server/Eureka endpoints if they do not use in-namespace services.
- `APP_CORS_ALLOWED_ORIGINS`, trusted proxy CIDR policy and release-specific
  immutable configuration label.
- Every image reference to an approved registry **digest**, not a mutable tag.
- `base/data-plane-config.yaml` private PostgreSQL hostname/port and all
  resource/replica policy based on measured staging load.
- `base/control-plane-config.yaml` protected Config Git URI/immutable label.

The base defaults to one replica and Compose-derived JVM memory limits only as a
staging template. It is not a production HA/capacity profile. A production
overlay must add measured replicas, PodDisruptionBudgets, topology spreading and
HPA policy after the traffic/SLO decision is approved.

## Secret contract

Secrets are referenced but never created here. An ExternalSecret/Vault/cloud
secret-manager controller or other approved platform mechanism must create these
Kubernetes delivery objects in the target namespace:

| Secret name | Required keys | Mounted paths / reader |
| --- | --- | --- |
| `delivery-shared-internal` | `value` | `internal-secret`; only services with internal HTTP credentials |
| `delivery-<service>-db` | `username`, `password` | `spring.datasource.username`, `spring.datasource.password`; each database-owning service gets its own secret/role |
| `delivery-auth-jwt` | `private.pem`, `public.pem` | Auth only at `/run/secrets/jwt-private.pem` and `/run/secrets/jwt-public.pem` |
| `delivery-config-repository` | `username`, `password` | Config Server Git credentials through config-tree property paths |

JWT retiring-key overlap, SMTP/OAuth, Firebase/FCM, backup and future payment
provider credentials are **not** silently enabled by this base. Add an owning
service-specific projected secret mapping in the generator and an approved
rotation/runbook before enabling a capability.

## Safe environment overlay workflow

1. Copy `overlays/staging-template/` into a private, environment-owned overlay.
   Do the same from `production-template/` only after platform decisions are
   approved.
2. Patch ConfigMaps, image names/digests and labels/annotations for the selected
   registry, cloud workload identity and environment.
3. Create secret delivery objects outside Git; verify files/permissions and
   missing-secret startup failure.
4. Add provider-specific Ingress/TLS/WAF only for `api-gateway`; use
   [`templates/gateway-ingress.example.yaml`](templates/gateway-ingress.example.yaml)
   as a starting shape, never as an apply-ready host/class/secret.
5. Add default-deny plus precise ingress/egress policies with approved DNS/data
   plane CIDRs/selectors; generic Kubernetes NetworkPolicy cannot safely express
   arbitrary managed-service FQDN access. See
   [`templates/network-policy.example.yaml`](templates/network-policy.example.yaml).
6. Render, policy-scan and dry-run in the target cluster. Deploy control plane →
   Auth → resource services → Gateway, then run the Gateway COD smoke and
   rollback rehearsal.

Once an approved overlay and reachable cluster exist, the guarded operator
sequence is:

```bash
K8S_OVERLAY=/secure/infra/delivery/overlays/staging \
KUBE_CONTEXT=approved-staging \
KUBE_NAMESPACE=delivery-staging \
CONFIRM_K8S_ROLLOUT=YES \
bash scripts/rollout-kubernetes.sh
```

For the no-legacy-token JWKS migration, additionally set
`JWKS_MIGRATION=true` and `K8S_JWKS_WAIT_SECONDS` to the approved 15-minute
access-token TTL plus clock-skew buffer. The script refuses placeholders,
plaintext Secrets, `LoadBalancer`/`NodePort` Services, missing namespace confirmation and a
JWKS migration with no wait. It has not been run in this workspace because no
Kubernetes API server/context is configured.

## Important limitations

- This package does not deploy PostgreSQL, Kafka, Redis, Elasticsearch,
  Prometheus/Grafana or a persistent tracing/logging backend. Those are data/
  observability platform choices and need HA, retention, access, backup and
  upgrade ownership.
- Config Server is configured for a protected Git backend, not the local native
  classpath repository. The Config Git authentication model must be chosen by
  the target platform.
- No production traffic, images, secrets, cloud resources or Kubernetes
  resources are created by rendering this package.

Read the cross-system decision gates in
[`docs/system/operations/deployment-foundation.md`](../../../docs/system/operations/deployment-foundation.md)
before constructing a real overlay.
