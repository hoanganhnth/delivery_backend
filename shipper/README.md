# Shipper

Root-level `shipper/` owns the service. The executable artifact and DNS identity
remain `shipper-service`; Compose builds `shipper/boot`.

- `domain/`: identity ownership, profile/availability/rating/read rules.
- `application-api/`: behavior-free commands, snapshots, use cases and ports.
- `application/`: profile/availability/rating decisions and identity lifecycle
  version-gap/BLOCKED convergence.
- `infrastructure/`: controllers/security, HTTP and database adapters, Flyway,
  canonical Kafka envelope/receipt handling and gated outbox relay.
- `boot/`: production entrypoint and deployment configuration.

```sh
mvn -B -pl :shipper-service -am clean verify
python3 -B scripts/verify-shipper-runtime.py
bash scripts/package-compose-services.sh shipper-service
```

Docker is required to execute PostgreSQL and Kafka integration tests. Missing
Docker reports skips and does not provide runtime proof. The Kafka/PostgreSQL
proof loads production configuration, enables consumer/relay on fixture topics,
and exercises raw JSON, replay, conflicting reuse, version gaps, DLT, committed
offsets, outbox publication and Tracking-failure rollback. Existing event IDs,
payload fingerprints, retry/DLT suffixes and production feature flags remain.

The packaged proof needs Docker, JDK 17 and OpenSSL. It owns a disposable
PostgreSQL container, local JWKS/Tracking fixtures and child JVMs. It verifies
RS256 authentication, ownership/admin/ratings reads, profile document images
and timestamps, availability/failure, identity outbox and migration restart.
It does not exercise a live Auth or Tracking deployment.

Profile insertion stores its identity outbox event in the same database
transaction. Identity Kafka processing stores the original event-ID receipt
with the projection transaction; the application decides lifecycle ordering
and offline convergence. All six original Java migrations live only in
infrastructure and retain their class names and bytes.
