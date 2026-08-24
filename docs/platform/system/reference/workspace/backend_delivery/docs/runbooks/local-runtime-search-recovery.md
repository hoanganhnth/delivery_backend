# Local Runtime Search Recovery

## Purpose

This runbook recovers the local Compose dependency chain:

`Elasticsearch -> search-service -> Gateway public search`

It is intended for Docker Desktop development/rehearsal only. It does not
provision a production search cluster and does not change the production
topology.

## Why this exists

The local stack starts many JVM services. On a Docker Desktop VM with limited
memory, Elasticsearch can be killed by the host with exit code `137` while its
named data volume remains valid. In that state `search-service` may be running
but public search returns a sanitized `503` until Elasticsearch is ready.

The recovery is deliberately non-recreating. Do not use `docker compose down
-v`, delete PostgreSQL/Kafka volumes, or recreate Elasticsearch as a first
response. In the current Compose file Elasticsearch state is still attached to
the existing container rather than a mounted named volume, so this runbook uses
`docker compose start`, never `up`, for Elasticsearch and Search.

## Recovery command

From `backend_delivery/`:

```bash
COMPOSE_PROJECT_NAME=backend_delivery \
  bash scripts/recover-local-search.sh
```

For a repeatable core MVP startup (which intentionally excludes the four
disabled capability services), use:

```bash
JAVA_HOME="$( /usr/libexec/java_home -v 17 )" \
  bash scripts/verify-runtime-startup.sh
```

The Compose topology itself now health-gates every application workload on the
private Config Server and Eureka readiness, so a plain `docker compose up` no
longer intentionally races fail-fast config import. The startup proof script is
still the preferred operator command because it additionally starts only the
supported COD core, waits for every application readiness check in dependency
order, and performs public Gateway smokes.

The shared application image gives the private readiness probe a 120-second
cold-start grace period and 12 subsequent 15-second attempts. This prevents a
slow JPA/Kafka/Eureka bootstrap on Docker Desktop from being marked unhealthy
before it can expose readiness; it does not turn a failing readiness response
into healthy traffic.

Set `RUNTIME_INCLUDE_DISABLED_CAPABILITIES=true` only for a full-capability
rehearsal on a Docker Desktop VM with sufficient memory. Those four services
are in the Compose `optional-capabilities` profile, so a normal
`docker compose up` leaves them stopped.

The script:

1. Verifies Docker and the Compose project owner.
2. Renders the Compose configuration before changing container state.
3. Stops only Search and the disabled capability services
   (`promotion`, `flashsale`, `analytics`, `livestream`) to release memory.
4. Starts the existing Elasticsearch container without rebuilding or
   recreating it.
5. Waits for the Docker healthcheck, then starts and waits for Search.
6. Retries the public Gateway search route for up to 120 seconds and requires
   HTTP `200`, allowing service discovery/routing to converge after Search has
   reported internal readiness.

It does not print secrets or token material. The disabled services remain
stopped intentionally. To attempt to restore them after the smoke, use:

```bash
COMPOSE_PROFILES=optional-capabilities \
  docker compose start promotion-service flashsale-service analytics-service livestream-service
```

Only do this after checking Docker Desktop memory; the services are not part of
the supported COD MVP path.

## Expected evidence

- Elasticsearch container: `running (healthy)`; a single local node may report
  cluster status `yellow` because replica shards are unassigned.
- Search container: `running (healthy)`.
- `GET /api/search/restaurants?...` through Gateway: HTTP `200`.
- Existing Elasticsearch indices and document counts remain intact.

An empty result set is valid when no restaurant/dish projection has been seeded;
HTTP `503` is not a valid readiness result after the recovery gate completes.

## If recovery fails

Inspect without deleting data:

```bash
docker inspect delivery-elasticsearch \
  --format 'status={{.State.Status}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}}'
docker compose logs --tail=160 elasticsearch search-service
docker stats --no-stream
```

If `OOMKilled=true` recurs, increase Docker Desktop memory or run only the
documented MVP services. Do not lower production search capacity based on this
local workaround. A production deployment must use an approved HA/managed
search choice, resource requests/limits, backup/rebuild policy and alerting.

## Production boundary

Local recovery proves only that the current projection can restart and serve a
bounded read. It does not prove search HA, shard recovery, sustained query
capacity, upgrade compatibility, or disaster recovery. Those are covered by
the production platform decision packet and must be tested in the selected
staging environment.
