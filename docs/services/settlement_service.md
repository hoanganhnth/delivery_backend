# Settlement service

Settlement keeps the executable Maven artifact and DNS identity `settlement-service`
(version `0.0.1-SNAPSHOT`, HTTP port `8090`). Its source layout is:

- `settlement/domain`: framework-free financial policies and value objects.
- `settlement/application-api`: use cases and inward-facing ports.
- `settlement/application`: financial workflows implementing those ports.
- `settlement/infrastructure`: HTTP, Kafka, JPA, provider and scheduler adapters,
  security, Spring composition and unchanged Flyway migrations.
- `settlement/boot`: Spring Boot entrypoint, runtime properties and the existing
  host regression/integration tests. Its production dependencies are infrastructure,
  Actuator and Prometheus.

The previous `settlement-service/` and `modules/settlement/` source paths are retired.
Java package names, layer artifact versions, HTTP/event shapes and financial flags
and defaults are unchanged. Compose builds with `SERVICE_PATH=settlement/boot`;
`scripts/package-compose-services.sh settlement-service` resolves the same path.
The crash-window harness automatically selects the relocated executable and rejects
fallback to a legacy JAR when `settlement/boot/pom.xml` exists.

Behavior authority remains the [Settlement feature](../platform/product/features/settlement.md)
and [finance workflow](../workflows/settlement_finance_flow.md). The relocation does
not enable payment, refund-provider, payout or financial mutation capabilities.

Validate the relocated reactor with:

```sh
mvn -o -B -pl :settlement-service -am clean verify
```

Existing host business-facing wrappers, provider handling, event parsing, transaction
boundaries and response mapping now live in infrastructure. Boot contains no such
logic. Persistence tests stay in infrastructure; host tests stay in boot to exercise
the same composed service.
