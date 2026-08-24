# Local Backend Runbook

## Prerequisites

- JDK 17 and Maven
- Docker Desktop/daemon with Compose v2
- `jq`, `curl` and OpenSSL

The Maven reactor is currently verified on JDK 17. Check `java -version` and
`mvn -version` before treating a compile failure on a newer JDK as a code failure.

## First startup

```bash
bash scripts/gen-keys.sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -DskipTests package
./scripts/verify-compose-config.sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
  RUNTIME_REBUILD_IMAGES=true \
  STARTUP_TIMEOUT_SECONDS=480 \
  bash scripts/verify-runtime-startup.sh
bash scripts/verify-observability-runtime.sh
```

The standard proof starts the 13-service COD core in dependency order and keeps
the four non-core capabilities stopped. A plain Compose command is useful for
interactive debugging, but it is not the rebuild acceptance gate.

Readiness alone is not treated as proof that a service can receive Gateway
traffic. After Auth and each resource service becomes healthy, the startup
script also waits for its `UP` lease in Eureka before starting the next stage.
If a recreated Config/Eureka control plane has lost an otherwise healthy
container's lease, the script recreates only that service once and rechecks its
registration; it never removes PostgreSQL or Kafka volumes. Set
`EUREKA_REGISTRATION_TIMEOUT_SECONDS` (default `120`) only when a constrained
machine needs a longer registration window.

`verify-runtime-startup.sh` defaults to reconciling existing images so a routine
health proof does not recreate every canonical application container. Set
`RUNTIME_REBUILD_IMAGES=true` after packaging a new release or on first local
startup; the isolated E2E runner sets it automatically. Image freshness remains
separately protected by `verify-docker-artifact-freshness.sh`.

Docker images consume the host-built Maven JARs. The shared Dockerfile compares
the packaged artifact with the module `pom.xml`, root reactor `pom.xml` and all
files under `src/`; image build fails with `run scripts/package-compose-services.sh first` instead of
silently deploying a stale JAR. The isolated regression proof is:

```bash
bash scripts/verify-docker-artifact-freshness.sh
```

The `JAVA_HOME` example is for macOS. On Linux, point `JAVA_HOME` at an installed
JDK 17 explicitly.

Before packaging, verify that all module parents and the Gateway BOM still match
the supported baseline:

```bash
./scripts/verify-build-baseline.sh
```

Base Compose publishes only Gateway (`http://localhost:8079`) for application
traffic. Service ports remain reachable inside `delivery-network`, which prevents
clients on the host from bypassing the public Gateway route policy. Resource
services still validate Bearer tokens independently through Auth JWKS.

PostgreSQL defaults to host port `5432`. If that port is already used by a local
installation, set `POSTGRES_HOST_PORT` (for example `15432`) before running
Compose; application containers still connect to `postgres:5432` internally.
For an isolated clean-data rehearsal without deleting the existing local volume,
also set `POSTGRES_VOLUME_NAME` to a new Docker volume name.

`scripts/gen-keys.sh` creates an ignored `.secrets/` keypair and writes only its
paths to ignored `.env`. `docker-compose.secrets.yml` injects those files as
Docker secrets under `/run/secrets`; neither keys nor a secret fallback are
packaged inside a JAR. Do not point those variables at a file in the source tree.

### Focused Auth/User/JWKS startup

When bringing up only the identity platform for local debugging, package the
affected host JARs first and always include the secret override: Auth has no
development key fallback and intentionally fails closed without its mounted RSA
keypair. If host PostgreSQL already owns `5432`, use a non-conflicting published
port; containers continue to use `postgres:5432`.

```bash
bash scripts/package-compose-services.sh \
  config-server discovery-server auth-service user-service api-gateway

POSTGRES_HOST_PORT=55432 \
  docker compose -f docker-compose.yml -f docker-compose.secrets.yml up -d \
  postgres kafka redis config-server discovery-server auth-service user-service api-gateway
```

Once Auth has registered with Eureka, restart only Gateway if it began before
that lease and returns a transient `503` for `/.well-known/jwks.json`. Do not
restart the whole stack or recreate PostgreSQL/Kafka volumes for this case.

For focused service debugging only, opt in to the port override:

```bash
docker compose -f docker-compose.yml -f docker-compose.secrets.yml \
  -f docker-compose.debug.yml up --build
```

Do not use the debug override as the normal client or deployment topology.

To deliberately rehearse the four non-core capabilities, enable the Compose
profile and provide enough Docker Desktop memory:

```bash
COMPOSE_PROFILES=optional-capabilities \
  docker compose -f docker-compose.yml -f docker-compose.secrets.yml \
  up -d --build livestream-service promotion-service analytics-service flashsale-service
```

Their source/API mappings do not make them part of the default COD runtime;
checkout/payment behavior remains separately gated.

## Optional providers

- FCM is disabled when `FIREBASE_SERVICE_ACCOUNT_KEY_PATH` is absent. To enable
  it, mount the JSON outside the image and pass a `file:` URI.
- Livestream credentials use `AGORA_APP_ID` and `AGORA_APP_CERTIFICATE`.
- Google social login is fail-closed until `GOOGLE_OAUTH_CLIENT_IDS` contains the
  allowed OAuth client ID(s), comma-separated.
- `scripts/gen-keys.sh` creates a local `INTERNAL_SECRET` in ignored `.env` once.
  Auth and user services require the same value for registration/linkage; blank
  configuration remains fail-closed.

## Validation

```bash
./scripts/verify-compose-config.sh
bash scripts/seed.sh
bash scripts/verify-mvp-cod-flow.sh
```

`scripts/test-order-flow.sh` được giữ làm compatibility wrapper và delegate sang
`verify-mvp-cod-flow.sh`; không còn implementation E2E thứ hai.

`scripts/seed.sh` mặc định ghi một ledger entry `LOCAL_SEED_DEPOSIT` idempotent
trị giá 500.000 VND cho shipper seed (đổi bằng `SHIPPER_DEPOSIT`). Đây là fixture
local chạy trực tiếp qua PostgreSQL container; không có route nạp tiền giả nào
được mở qua Gateway. Settlement service phải boot ít nhất một lần để schema tồn
tại trước khi chạy seed.

Vì public Auth registration không còn tạo mới `SHIPPER`, seed dùng auth-service
one-shot operator runner để provision fixture SHIPPER qua AuthService + User
internal provisioning, sau đó mới login và tạo Shipper profile qua Gateway. Trước
mỗi run, script đưa các shipper fixture cũ `shipper+*@test.dev` offline qua API
để Match không offer nhầm cho dữ liệu test cũ. Runner này không tạo ADMIN.

The two flow scripts use Gateway only. A successful Compose render or Maven
package does not prove the order lifecycle; record container health/logs and the
flow script result separately.

### Disposable full-stack E2E

Use the clean runner when the proof must start from empty PostgreSQL/Kafka
volumes rather than append fixtures to the canonical local stack:

```bash
# Render and prove the isolation boundary without starting containers.
CLEAN_E2E_CONFIG_ONLY=true bash scripts/verify-clean-compose-e2e.sh

# Start a separate project, run COD + WebSocket + settlement/failure-matrix
# proof, then remove only that project's containers and run-scoped volumes.
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
  bash scripts/verify-clean-compose-e2e.sh
```

The runner uses `docker-compose.isolated-e2e.yml`: it removes fixed container
names and infrastructure host ports, gives Gateway a Docker-assigned loopback
port, and uses project-scoped PostgreSQL/Kafka volumes. It must not stop,
recreate, inspect or mount the canonical `backend_delivery` containers/volumes.
It passes the same `COMPOSE_FILE`/project environment to seed, COD and
failure-matrix children, including their PostgreSQL/Kafka fixture commands, so
those children cannot fall back to the canonical Compose project. The build
baseline statically rejects a direct `docker compose exec` regression in that
path.
It still starts a second full COD stack, so run it only when Docker Desktop has
enough free memory; it is a local proof, not a production load/HA test.

Capacity is a hard safety gate, not a reason to stop the existing stack. On
2026-08-08 this workstation's Docker Desktop had 7.75 GiB total memory while
the 22-container canonical core used roughly 6.2 GiB, so a concurrent second
core was deliberately not started. Use a dedicated CI/runner or raise Docker
Desktop capacity before the full runner; do not free memory by taking down the
canonical developer project.

Do not run `docker compose down -v` against an existing developer environment
unless removal of local databases is intentional.
