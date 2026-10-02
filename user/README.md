# User service

Canonical service sources live here; Maven/runtime identity remains `user-service`.

- `domain`: provisioning identity and lifecycle version rules, without frameworks.
- `application-api`: behavior-free commands, results and ports.
- `application`: registration, identity binding, profile/address access,
  default-address transitions, block/unblock and lifecycle projection.
- `infrastructure`: HTTP/security/JWKS, JPA, owner row locks, outbox/inbox,
  Kafka retries/DLT, Flyway and Spring composition.
- `boot`: entrypoint, runtime configuration and packaged runtime integration tests.

Provisioning verifies Auth's RS256 handoff locally through JWKS. Profile insertion
and identity outbox are one transaction; retries and concurrent registrations
converge on the same immutable identity. Address changes run inside an owner-row
lock; deleting a default promotes the latest remaining address. Kafka receipts
and projection updates share a transaction. Auth owns lifecycle policy; User
rejects initialized version gaps and accepts the first authoritative snapshot.

From the backend root:

```sh
mvn -B -pl :user-service -am clean verify
python3 -B scripts/verify-user-runtime.py
bash scripts/package-compose-services.sh user-service
```

The tests use disposable PostgreSQL/Kafka fixtures and need Docker. The packaged
proof also needs Java and OpenSSL; it verifies real signed provisioning/access
JWTs, HTTP ownership/default transitions, outbox, block/unblock and database
restart. Event consumption and outbox relay keep their existing opt-in flags
`IDENTITY_EVENTS_ENABLED` and `IDENTITY_OUTBOX_RELAY_ENABLED`.
