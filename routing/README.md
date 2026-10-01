# Routing

Standalone Routing service, organized as one root-level Maven reactor:

| Directory | Responsibility |
| --- | --- |
| `domain` | Validated coordinates/queries/results and routing invariants |
| `application-api` | Routing use case and provider ports |
| `application` | Route/matrix fallback and ETA window decisions |
| `infrastructure` | Private HTTP endpoints, wire mapping, credential checks, Mapbox adapter and composition configuration |
| `boot` | Spring Boot entrypoint, deployment properties and executable JAR |

The service/application/artifact identity remains `routing-service`; internal
DNS and `/internal/routing/v1/{route,matrix,eta-window}` are unchanged.
The old root `routing-service/` and `modules/routing/` paths are replaced by
this directory. No database migration or client API migration is required.

From the backend root:

```sh
mvn -B -pl :routing-service -am clean verify
python3 scripts/verify-routing-runtime.py
bash scripts/package-compose-services.sh routing-service
```

The runtime proof launches the packaged JAR on ephemeral loopback ports with
a test-only credential and local HTTP provider fixture; it verifies readiness,
authorization, all three endpoints, provider mapping and fallback. It does not
contact Mapbox or prove live-provider latency. The proof uses a 5-second fixture
timeout to avoid conflating cold WebClient initialization with contract
behavior; production retains its existing 450 ms default.

`docker-compose.yml` builds this service with `SERVICE_PATH=routing/boot`.
`scripts/package-compose-services.sh` accepts both the stable service identity
and the explicit boot path, and writes the existing artifact freshness manifest.
For normal deployments, provide `INTERNAL_SECRET` or the `internal-secret`
Config Tree credential and keep the existing Config Server/discovery topology.

Revert the Routing consolidation commit to restore the previous source/build
paths. The deployment image/service names and wire contracts are preserved.
