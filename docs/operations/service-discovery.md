# Service Discovery And Naming

The canonical registry is Eureka. It resolves a healthy instance by logical
name; it does not create another public edge. Client HTTP and raw tracking
WebSocket traffic still enter only through `api-gateway`.

## Canonical names

Eureka IDs are stable lowercase module names. Do not use container IDs, ports,
or display names in a client URL.

| Active MVP service | Eureka ID |
| --- | --- |
| Gateway | `api-gateway` |
| Identity | `auth-service`, `user-service` |
| Ordering | `restaurant-service`, `order-service`, `delivery-service`, `shipper-service`, `settlement-service` |
| Realtime/workflow | `notification-service`, `tracking-service`, `match-service`, `saga-orchestrator-service` |
| Search | `search-service` |

`promotion-service`, `flashsale-service`, `analytics-service`, and
`livestream-service` remain disabled MVP capabilities. They are deliberately
not Eureka clients in this rollout.

Gateway routes use `lb://<service-id>` (and `lb:ws://tracking-service` for raw
WebSocket). The five active synchronous internal clients use
`http://<service-id>` through Spring Cloud LoadBalancer. No service port is
published to the host.

## Environment topology

- Local Compose: one private `discovery-server` and `config-server`; all active
  services set `SERVICE_DISCOVERY_ENABLED=true`, fetch required configuration,
  then register their Actuator metadata. Registry/config ports are only exposed
  to `delivery-network`.
- Staging: private Eureka and Config Server, backed by a protected versioned
  configuration repository. Deploy each service with an immutable config label.
- Production: at least two Eureka and Config Server instances across failure
  domains behind private networking. Registry/config endpoints, management
  endpoints, service ports and databases never receive a public load balancer.

An instance registers its canonical app name, unique instance ID, management
port and readiness path. Eureka must show `UP` before Gateway traffic is
eligible; a process merely listening on its application port is not ready.

## Local validation and recovery

After `bash scripts/gen-keys.sh` and a fresh Maven package:

```sh
docker compose -f docker-compose.yml -f docker-compose.secrets.yml up --build
docker compose exec discovery-server wget -qO- http://localhost:8761/eureka/apps
docker compose restart order-service
docker compose exec discovery-server wget -qO- http://localhost:8761/eureka/apps/ORDER-SERVICE
```

Wait for the restarted instance to become `UP`, then run the Gateway-only COD
smoke (`bash scripts/verify-mvp-cod-flow.sh`). To recover from a verified
registry incident without opening service ports, use the explicit versioned
overlay:

```sh
docker compose -f docker-compose.yml -f docker-compose.static-routes.yml up -d
```

It disables discovery and restores only private static routes inside the
Gateway. Validate the Gateway COD smoke, fix the registry, remove the overlay,
and redeploy discovered routing. `scripts/verify-compose-config.sh` renders and
asserts both modes.
