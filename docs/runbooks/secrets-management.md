# Secrets Management And Rotation

Secrets never belong in the Config Server repository, image layers, tracked
files, logs, command arguments, or ordinary environment variables. Local Compose
uses Docker secret files under ignored `.secrets/`; staging/production inject
the same files through workload identity from Vault, a cloud secret manager, or
Kubernetes ExternalSecret. Kubernetes Secret is a delivery object, not a source
repository fallback.

## Create and access

Run `bash scripts/gen-keys.sh` locally. It creates private operator files and
writes paths—not values—to `.env`. Compose mounts `internal-secret` and
`spring.datasource.password` through Spring config-tree and JWT files under
`/run/secrets`. `scripts/verify-secrets.sh` scans tracked material and CI runs
it as a required gate.

| Secret | Reader(s) | Access policy |
| --- | --- | --- |
| JWT private key | `auth-service` | Auth workload only |
| JWT public / retiring public key | `auth-service` | Auth publishes public JWKS; no other workload mounts JWT public keys |
| Internal credential | Auth, User, Restaurant, Order, Delivery, Settlement, Notification, Match, Tracking; disabled internal services when deployed | named workload identities only |
| Database credential | PostgreSQL and each database-owning service | per-service database role; no client workload access |
| Kafka/Redis credential | only services that use the protected deployment profile | topic/key-prefix scoped service identity |
| Firebase/FCM credential | Notification | Notification workload only |
| OAuth/provider secret | Auth or explicitly enabled provider service | owning workload only |

Audit secret-manager reads by workload identity, secret name/version, deployment
ID and time. Never log the secret value, mounted-file content, full environment,
or decoded JWT key material.

## Rotation and recovery

1. Create a new secret version; grant it only to the intended workload identity.
2. For ordinary credentials, deploy a canary with the new version, verify
   readiness and Gateway COD smoke, then roll remaining instances. Revoke the
   old version only after all consumers move.
3. For JWT, deploy `docker-compose.jwt-overlap.yml` (or its platform
   equivalent) with the retiring public key and `JWT_RETIRING_KID` equal to the
   old token header `kid`. Auth publishes both public JWKs; services retrieve
   them from `/.well-known/jwks.json`. Publish/wait for the 5-minute JWKS cache,
   switch `JWT_ACTIVE_KID`, retain the old JWK for access TTL (15 minutes) plus
   clock skew, and retain Auth's prior public key for the 7-day refresh window.
   Gateway never receives or verifies JWT keys.
4. If a secret is exposed, revoke it, rotate dependents, invalidate affected
   sessions/credentials where appropriate, and preserve audit evidence without
   copying the value into an incident ticket.

Missing JWT, DB, config-server, or deployment-required internal secret is a
startup failure. This is safer than a fallback credential or a half-functional
instance.
