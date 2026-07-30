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
docker compose -f docker-compose.yml -f docker-compose.secrets.yml up --build
```

Docker images consume the host-built Maven JARs. The shared Dockerfile compares
the packaged artifact with the module `pom.xml`, root reactor `pom.xml` and all
files under `src/`; image build fails with `run Maven package first` instead of
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
clients on the host from bypassing Gateway JWT/header policy.

PostgreSQL defaults to host port `5432`. If that port is already used by a local
installation, set `POSTGRES_HOST_PORT` (for example `15432`) before running
Compose; application containers still connect to `postgres:5432` internally.
For an isolated clean-data rehearsal without deleting the existing local volume,
also set `POSTGRES_VOLUME_NAME` to a new Docker volume name.

`scripts/gen-keys.sh` creates an ignored `.secrets/` keypair and writes only its
paths to ignored `.env`. `docker-compose.secrets.yml` injects those files as
Docker secrets under `/run/secrets`; neither keys nor a secret fallback are
packaged inside a JAR. Do not point those variables at a file in the source tree.

For focused service debugging only, opt in to the port override:

```bash
docker compose -f docker-compose.yml -f docker-compose.secrets.yml \
  -f docker-compose.debug.yml up --build
```

Do not use the debug override as the normal client or deployment topology.

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

Do not run `docker compose down -v` against an existing developer environment
unless removal of local databases is intentional.
