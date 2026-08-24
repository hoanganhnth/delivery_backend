# 0002 Phase 3 Runtime Topology

Date: 2026-07-30

## Status

Accepted

## Context

The delivery backend currently uses Compose DNS/static URLs for Gateway and
the small number of synchronous internal calls. Its post-MVP roadmap requires
Eureka, centrally versioned configuration, managed secrets, and a deployment
procedure that only routes healthy instances. The public contract requires all
client HTTP and WebSocket traffic to enter through the API Gateway.

## Decision

- Canonical service IDs are the existing lowercase hyphenated module names:
  `api-gateway`, `auth-service`, `user-service`, `restaurant-service`,
  `order-service`, `delivery-service`, `shipper-service`, `search-service`,
  `settlement-service`, `notification-service`, `tracking-service`,
  `match-service`, `saga-orchestrator-service`, and the disabled
  `promotion-service`, `flashsale-service`, `analytics-service`, and
  `livestream-service`. Eureka registrations use exactly these IDs.
- Local Compose runs a private Eureka registry and Spring Cloud Config Server.
  Services register with Eureka and obtain versioned non-secret configuration
  from Config Server before starting. Only Gateway publishes an application
  port; management and registry ports are private to the Compose network.
- Staging and production run Config Server and Eureka in private network zones
  with at least two registry instances in production. Configuration is supplied
  from a protected, versioned Git repository at an immutable tag/commit label.
  A deployment passes the label as an input and never edits live configuration.
- Spring Cloud Config excludes all secret material. Local Compose uses Docker
  secrets from ignored operator files. Staging/production use workload identity
  to inject a managed secret (Vault, cloud secret manager, or Kubernetes
  ExternalSecret); native Kubernetes Secret is only the delivery mechanism,
  never a repository fallback.
- Discovery routes use `lb://<service-id>`. The versioned
  `docker-compose.static-routes.yml` overlay is the emergency rollback; it sets
  static private URLs and disables Eureka without publishing direct service
  ports. It is a recovery path, not a production topology.
- Config refresh is off. A config change is released by choosing a new label
  and rolling restart/canary, preventing a running transaction from observing
  mixed configuration.

## Alternatives Considered

1. Keep Compose DNS/static URLs: simple locally but cannot register/rebalance
   transient instances or meet the roadmap’s discovery requirement.
2. Use Kubernetes DNS or Consul now: viable later but requires replacing the
   explicitly selected Eureka migration and introduces a second discovery
   technology in this phase.
3. Enable Spring Cloud Bus refresh: reduces restart time but permits mixed
   configuration during in-flight work and has no current transaction-safety
   contract.

## Consequences

Positive:

- Gateway and internal callers target logical services, preserve the public
  boundary, and remove production dependence on localhost/static instances.
- An operator can audit registered names, health, metadata, config version, and
  secret access by workload identity.
- Immutable config labels and the static overlay provide deterministic rollback.

Tradeoffs:

- Local startup has additional registry/config dependencies.
- Secrets are prepared by the deployment environment and intentional missing
  secrets fail startup instead of being silently defaulted.
- Production HA requires registry/config replicas and protected config-repo
  credentials outside this source repository.

## Follow-Up

- Implement discovery/config services, client bootstrap, operator runbooks and
  CI gates in the Phase 3 execution plan.
- Revisit Eureka only through a new ADR if a Kubernetes-native discovery
  migration is later selected.
