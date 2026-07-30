# Centralized Configuration

`config-server` is the source for versioned, non-secret platform and routing
configuration. Its local native repository is packaged only for Compose;
staging and production set `CONFIG_SERVER_BACKEND=git` and use a protected Git
repository. The repository contains placeholders/defaults, never credential
values or key files.

## Classification and authority

| Class | Examples | Authority and delivery |
| --- | --- | --- |
| Business | enabled MVP flags, order policy, rate limits | reviewed config repository, immutable label |
| Infrastructure | logical routes, Kafka/Redis hosts, telemetry, pool/timeouts | reviewed config repository and environment deployment values |
| Environment | profile, registry/config endpoint, instance metadata | deployment manifest; staging/prod never use localhost |
| Secret | JWT key, internal credential, DB/Kafka/Redis password, FCM and OAuth credential | Docker/Kubernetes/external secret manager only |

Client applications import Config Server before boot. Compose uses a non-optional
`configserver:` import with `CONFIG_SERVER_FAIL_FAST=true`; unavailable or
malformed required config stops startup. Focused JVM tests retain an optional
direct-run import so they do not require network infrastructure.

## Profiles, versioning and rollback

- Local uses the packaged native config repository and Compose topology.
- Staging and production use separate config-repository access and separate
  immutable labels. A staging label cannot be promoted by changing a service
  environment variable in place; deploy the selected label with the release.
- Validate a proposed label by starting a fresh canary with required config,
  checking `/actuator/health/readiness`, discovery registration and a Gateway
  smoke. Record the service image digest and config label together.
- Roll back by redeploying the prior image/config label. Do not edit the live
  branch to emulate rollback.

Dynamic refresh is intentionally disabled. A changed config is observed only
by a restarted/rolled instance, so an in-flight transaction cannot see a mixed
configuration. A future refresh mechanism needs its own transaction-safety
decision and proof.
