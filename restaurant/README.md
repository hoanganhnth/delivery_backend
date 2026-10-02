# Restaurant

The root `restaurant/` directory owns the service layers. The executable artifact and DNS identity remain `restaurant-service`; Compose builds `restaurant/boot`.

- `domain`: restaurant/menu/ownership and serviceability geometry rules, without framework dependencies.
- `application-api`: use-case commands/results and ports.
- `application`: catalog/profile use cases, rating, restaurant order decisions, serviceability management/evaluation, inventory reservations/order-event processing, catalog lifecycle and canonical checkout validation.
- `infrastructure`: HTTP/Kafka/JPA adapters, migrations and Spring composition.
- `boot`: production entrypoint/runtime properties and Spring/database integration tests.

Consolidation is still in progress: core extraction is implemented; final adapter/dependency and Docker audits remain. See `docs/plans/active/service-architecture-consolidation.md` for the authoritative sequence and proof.

From the repository root, run `mvn -B -pl :restaurant-service -am clean verify`. Docker is required for PostgreSQL integration tests.

Kafka/PostgreSQL proof loads production configuration and enables actual inventory consumer/retry/DLT and leased Search outbox relay. It also exercises checkout with both serviceability and inventory enabled. Packaged HTTP/JWT, restart and resolved-dependency proofs pass; Docker and final service audit are still pending.

Run `python3 -B scripts/verify-restaurant-runtime.py` after building the boot JAR. The proof owns disposable PostgreSQL/JWKS fixtures and verifies real RS256 authentication, ownership, checkout with serviceability/inventory, private inventory credentials, lifecycle audit/outbox and restart against the existing schema. Kafka transport is covered separately by `RestaurantKafkaPostgresIntegrationTest`; these fixtures do not prove a live Auth/Order/Search deployment.
