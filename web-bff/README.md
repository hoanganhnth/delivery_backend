# Web BFF

The executable service remains `web-bff-service`; its source lives in `boot/`.
There is no parallel legacy implementation.

- `domain/`: opaque session identity, CSRF verification and session state.
- `application-api/`: use cases and dependency inversion ports.
- `application/`: login/current/refresh/logout, access resolution and proxy policy.
- `infrastructure/`: HTTP controllers, Auth/Gateway clients, token encryption,
  PostgreSQL persistence, Flyway migration and Spring composition.
- `boot/`: production entrypoint and deployment configuration.

```sh
mvn -B -pl :web-bff-service -am clean verify
python3 -B scripts/verify-web-bff-runtime.py
bash scripts/package-compose-services.sh web-bff-service
```

The integration tests need Docker to run PostgreSQL. Without Docker,
Testcontainers tests report skips; that does not constitute runtime proof.
The packaged proof requires Docker and JDK 17, creates its own PostgreSQL
container and local Auth/Gateway fixtures, and removes only its own resources.
It checks production startup, migration history on restart, HTTP adapters,
login/refresh/logout, encrypted storage, origin/CSRF and proxy boundaries.
It does not exercise a live Auth/Gateway deployment or external credentials.

Refresh claims commit before the upstream request. Completion and logout use
an exclusive row lock through `Ports.Sessions.mutate`; upstream calls stay
outside that transaction. Rejection revocation commits before the application
throws. This preserves local revocation when refresh and logout overlap.

The original SQL V1 migration is preserved byte-for-byte. The duplicate Java
V1 and legacy entity/repository are removed; schema ownership belongs solely
to infrastructure. No new database migration is introduced by this move.
