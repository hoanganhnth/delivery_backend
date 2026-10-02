# Restaurant

The root `restaurant/` directory owns the service layers. The executable artifact and DNS identity remain `restaurant-service`; Compose builds `restaurant/boot`.

- `domain`: restaurant/menu/ownership and serviceability geometry rules, without framework dependencies.
- `application-api`: use-case commands/results and ports.
- `application`: catalog/profile use cases, rating, restaurant order decisions serviceability management/evaluation and inventory reservations.
- `infrastructure`: HTTP/Kafka/JPA adapters, migrations and Spring composition.
- `boot`: production entrypoint/runtime properties and Spring/database integration tests.

Consolidation is still in progress: inventory event processing, catalog lifecycle and order-cache workflows await core extraction. See `docs/plans/active/service-architecture-consolidation.md` for the authoritative sequence and proof.

From the repository root, run `mvn -B -pl :restaurant-service -am clean verify`. Docker is required for PostgreSQL integration tests.
