#!/usr/bin/env bash
set -euo pipefail

command -v docker >/dev/null
command -v jq >/dev/null

# Contract rendering references an ignored operator-owned secret file. The
# renderer never reads this placeholder; a real Compose startup requires it.
if [[ -z "${INTERNAL_SECRET_FILE:-}" ]]; then
  INTERNAL_SECRET_FILE="/tmp/compose-contract-test-internal-secret"
  export INTERNAL_SECRET_FILE
fi
if [[ -z "${DB_PASSWORD_FILE:-}" ]]; then
  DB_PASSWORD_FILE="/tmp/compose-contract-test-db-password"
  export DB_PASSWORD_FILE
fi
if [[ -z "${GRAFANA_ADMIN_PASSWORD:-}" ]]; then
  GRAFANA_ADMIN_PASSWORD="compose-contract-test-grafana-password"
  export GRAFANA_ADMIN_PASSWORD
fi
if [[ -z "${JWT_PRIVATE_KEY_FILE:-}" ]]; then
  JWT_PRIVATE_KEY_FILE="/tmp/compose-contract-test-jwt-private.pem"
  export JWT_PRIVATE_KEY_FILE
fi
if [[ -z "${JWT_PUBLIC_KEY_FILE:-}" ]]; then
  JWT_PUBLIC_KEY_FILE="/tmp/compose-contract-test-jwt-public.pem"
  export JWT_PUBLIC_KEY_FILE
fi
if [[ -z "${JWT_PREVIOUS_PUBLIC_KEY_FILE:-}" ]]; then
  JWT_PREVIOUS_PUBLIC_KEY_FILE="/tmp/compose-contract-test-jwt-previous-public.pem"
  export JWT_PREVIOUS_PUBLIC_KEY_FILE
fi
if [[ -z "${JWT_RETIRING_KID:-}" ]]; then
  JWT_RETIRING_KID="compose-contract-test-retiring-kid"
  export JWT_RETIRING_KID
fi

default_profile_config="$(docker compose config --format json)"
COMPOSE_PROFILES=optional-capabilities docker compose config --quiet
rendered_config="$(COMPOSE_PROFILES=optional-capabilities docker compose config --format json)"
COMPOSE_PROFILES=optional-capabilities docker compose -f docker-compose.yml -f docker-compose.secrets.yml config --quiet
rendered_secret_config="$(COMPOSE_PROFILES=optional-capabilities docker compose -f docker-compose.yml -f docker-compose.secrets.yml config --format json)"
COMPOSE_PROFILES=optional-capabilities docker compose -f docker-compose.yml -f docker-compose.secrets.yml -f docker-compose.jwt-overlap.yml config --quiet
rendered_rotation_config="$(COMPOSE_PROFILES=optional-capabilities docker compose -f docker-compose.yml -f docker-compose.secrets.yml -f docker-compose.jwt-overlap.yml config --format json)"
rendered_static_route_config="$(COMPOSE_PROFILES=optional-capabilities docker compose -f docker-compose.yml -f docker-compose.static-routes.yml config --format json)"
rendered_isolated_e2e_config="$(
  COMPOSE_PROFILES=optional-capabilities \
  COMPOSE_PROJECT_NAME=delivery_e2e_contract \
  POSTGRES_VOLUME_NAME=delivery_e2e_contract_postgres \
  KAFKA_VOLUME_NAME=delivery_e2e_contract_kafka \
    docker compose -f docker-compose.yml -f docker-compose.secrets.yml \
      -f docker-compose.isolated-e2e.yml config --format json
)"
expected_kafka_volume_name="${KAFKA_VOLUME_NAME:-backend_delivery_kafka_data}"

printf '%s' "$default_profile_config" | jq -e '
  . as $root
  | ([
    "promotion-service", "flashsale-service", "analytics-service",
    "livestream-service"
  ] | all(. as $service | ($root.services | has($service) | not)))
' >/dev/null

printf '%s' "$rendered_config" | jq -e \
  --arg expectedKafkaVolumeName "$expected_kafka_volume_name" '
  . as $root
  | .services["promotion-service"].environment.SPRING_DATASOURCE_URL
      == "jdbc:postgresql://postgres:5432/promotion_db"
    and .services.kafka.environment.KAFKA_AUTO_CREATE_TOPICS_ENABLE == "true"
    and .services.kafka.environment.KAFKA_HEAP_OPTS == "-Xms256m -Xmx384m"
    and (.services.kafka.volumes | length == 1)
    and .services.kafka.volumes[0].type == "volume"
    and .services.kafka.volumes[0].target == "/var/lib/kafka/data"
    and .volumes.kafka_data.name == $expectedKafkaVolumeName
    and .services.postgres.environment.POSTGRES_PASSWORD_FILE == "/run/secrets/db-password"
    and (.services.postgres.secrets | map(.source) | index("db-password") != null)
    and ([
      "auth-service", "user-service", "restaurant-service", "order-service",
      "delivery-service", "match-service", "shipper-service", "settlement-service",
      "notification-service", "livestream-service", "saga-orchestrator-service",
      "promotion-service", "analytics-service", "flashsale-service"
    ] | all(. as $service |
      ($root.services[$service].environment | has("SPRING_DATASOURCE_PASSWORD") | not)
      and ($root.services[$service].secrets | map(.source) | index("db-password") != null)))
    and ([
      "auth-service", "user-service", "restaurant-service", "order-service",
      "delivery-service", "settlement-service", "notification-service",
      "match-service", "tracking-service", "promotion-service", "flashsale-service"
    ] | all(. as $service |
      ($root.services[$service].environment | has("INTERNAL_SECRET") | not)
      and ($root.services[$service].secrets | map(.source) | index("internal-secret") != null)))
    and (.services["user-service"].environment | has("USER_LEGACY_DELETE_API_ENABLED") | not)
    and .services["auth-service"].environment.JWT_ACCESS_TOKEN_TTL_SECONDS == "900"
    and .services["auth-service"].environment.MANAGEMENT_HEALTH_MAIL_ENABLED == "false"
    and (.services["restaurant-service"].environment | has("ORDER_SERVICE_URL") | not)
    and .services["restaurant-service"].environment.SPRING_DATA_REDIS_HOST == "redis"
    and (.services["restaurant-service"].depends_on | has("redis"))
    and .services["promotion-service"].environment.PROMOTION_CHECKOUT_ENABLED == "false"
    and .services["promotion-service"].environment.PROMOTION_OUTBOX_RELAY_ENABLED == "false"
    and .services["promotion-service"].environment.PROMOTION_MERCHANT_CREATE_API_ENABLED == "false"
    and .services["flashsale-service"].environment.FLASHSALE_CHECKOUT_ENABLED == "false"
    and .services["flashsale-service"].environment.FLASHSALE_OUTBOX_RELAY_ENABLED == "false"
    and .services["flashsale-service"].environment.FLASHSALE_MERCHANT_REGISTRATION_ENABLED == "false"
    and (.services["restaurant-service"].environment | has("RESTAURANT_OPS_API_ENABLED") | not)
    and (.services["restaurant-service"].environment | has("RESTAURANT_LOCATION_API_ENABLED") | not)
    and (.services["order-service"].environment | has("ORDER_LEGACY_DASHBOARD_ENABLED") | not)
    and (.services["order-service"].environment | has("ORDER_LEGACY_MUTATION_API_ENABLED") | not)
    and (.services["order-service"].environment | has("ORDER_LEGACY_READ_API_ENABLED") | not)
    and .services["order-service"].environment.ORDER_PAYMENT_EVENT_PROCESSING_ENABLED == "false"
    and .services["order-service"].environment.ORDER_VOUCHER_CHECKOUT_ENABLED == "false"
    and .services["order-service"].environment.ORDER_FLASHSALE_CHECKOUT_ENABLED == "false"
    and .services["order-service"].environment.PROMOTION_SERVICE_URL
      == "http://promotion-service:8096"
    and .services["order-service"].environment.FLASHSALE_SERVICE_URL
      == "http://flashsale-service:8092"
    and (.services["delivery-service"].environment | has("DELIVERY_LEGACY_ASSIGNMENT_API_ENABLED") | not)
    and (.services["delivery-service"].environment | has("SPRING_DATA_REDIS_HOST") | not)
    and (.services["delivery-service"].depends_on | has("redis") | not)
    and (.services["delivery-service"].environment
      | has("DELIVERY_WEBSOCKET_ENABLED") | not)
    and .services["settlement-service"].environment.PAYMENT_PROCESSING_ENABLED == "false"
    and .services["settlement-service"].environment.FAKE_PAYMENT_PROVIDER_ENABLED == "false"
    and .services["settlement-service"].environment.SETTLEMENT_SELF_SERVICE_API_ENABLED == "false"
    and .services["settlement-service"].environment.SETTLEMENT_ADMIN_MUTATION_API_ENABLED == "false"
    and .services["shipper-service"].environment.SHIPPER_LEGACY_RATING_WRITE_API_ENABLED == "false"
    and .services["shipper-service"].environment.SHIPPER_LEGACY_DELETE_API_ENABLED == "false"
    and (.services["search-service"].environment | has("SPRING_DATA_REDIS_HOST") | not)
    and (.services["search-service"].depends_on | has("redis") | not)
    and .services["livestream-service"].environment.LIVESTREAM_API_ENABLED == "false"
    and .services["analytics-service"].environment.ANALYTICS_PROCESSING_ENABLED == "false"
    and (.services["notification-service"].environment
      | has("NOTIFICATION_WEBSOCKET_ENABLED") | not)
    and .services["flashsale-service"].environment.RESTAURANT_SERVICE_URL
      == "http://restaurant-service:8083"
    and (.services["auth-service"].environment | has("USER_SERVICE_URL") | not)
    and (.services["api-gateway"].environment
      | has("APP_SAGA_ORCHESTRATOR_SERVICE_URI") | not)
    and (.services["api-gateway"].environment.APP_CORS_ALLOWED_ORIGINS | length > 0)
    and (.services["api-gateway"].environment.APP_CORS_ALLOWED_ORIGINS
      | contains("http://localhost:4173"))
    and (.services["api-gateway"].environment.APP_CORS_ALLOWED_ORIGINS
      | contains("http://127.0.0.1:4173"))
    and (.services["tracking-service"].environment | has("DELIVERY_SERVICE_URL") | not)
    and .services["tracking-service"].environment.TRACKING_PUBLISHER_DISCONNECT_GRACE_SECONDS == "30"
    and .services["tracking-service"].environment.TRACKING_PUBLISHER_LEASE_TTL_SECONDS == "120"
    and .services["tracking-service"].environment.TRACKING_PUBLISHER_EXPIRY_SWEEP_INTERVAL_MS == "5000"
    and .services["tracking-service"].environment.TRACKING_PUBLISHER_EXPIRY_SWEEP_BATCH_SIZE == "100"
    and .services["tracking-service"].environment.TRACKING_PUBLISHER_EXPIRY_CLAIM_SECONDS == "30"
    and ([
      "notification-service",
      "match-service",
      "tracking-service",
      "flashsale-service"
    ] | all(. as $service |
      $root.services[$service].environment.SPRING_DATA_REDIS_HOST == "redis"))
    and ([
      "order-service",
      "delivery-service",
      "match-service",
      "saga-orchestrator-service",
      "analytics-service"
    ] | all(. as $service |
      $root.services[$service].environment.SPRING_KAFKA_BOOTSTRAP_SERVERS == "kafka:9092"))
    and (["postgres", "redis", "kafka", "elasticsearch", "prometheus", "grafana"]
      | all(. as $service | $root.services[$service].healthcheck != null))
    and $root.services.prometheus.healthcheck.test[1]
      == "wget -q -T 3 -O /dev/null http://localhost:9090/-/ready"
    and $root.services.grafana.healthcheck.test[1]
      == "wget -q -T 3 -O /dev/null http://localhost:3000/api/health"
    and $root.services.grafana.depends_on.prometheus.condition == "service_healthy"
    and ($root.services["config-server"].healthcheck != null)
    and ($root.services["discovery-server"].healthcheck != null)
    and ($root.services["config-server"].ports // [] | length == 0)
    and ($root.services["discovery-server"].ports // [] | length == 0)
    and ([
      "api-gateway", "auth-service", "user-service", "restaurant-service",
      "order-service", "delivery-service", "search-service", "shipper-service",
      "settlement-service", "notification-service", "match-service",
      "tracking-service", "livestream-service", "saga-orchestrator-service",
      "promotion-service", "analytics-service", "flashsale-service"
    ] | all(. as $service |
      $root.services[$service].depends_on["config-server"].condition
        == "service_healthy"
      and $root.services[$service].depends_on["discovery-server"].condition
        == "service_healthy"))
    and ([
      "promotion-service", "flashsale-service", "analytics-service",
      "livestream-service"
    ] | all(. as $service |
      ($root.services[$service].profiles | index("optional-capabilities")) != null))
    and ([
      "api-gateway", "auth-service", "user-service", "restaurant-service",
      "order-service", "delivery-service", "search-service", "shipper-service",
      "settlement-service", "notification-service", "match-service",
      "tracking-service", "livestream-service", "saga-orchestrator-service",
      "promotion-service", "analytics-service", "flashsale-service"
    ] | all(. as $service |
      $root.services[$service].environment.SPRING_CONFIG_IMPORT
        == "configserver:http://config-server:8888,optional:configtree:/run/secrets/"
      and $root.services[$service].environment.CONFIG_SERVER_FAIL_FAST == "true"
      and $root.services[$service].environment.SERVICE_DISCOVERY_ENABLED == "true"
      and $root.services[$service].environment.EUREKA_DEFAULT_ZONE
        == "http://discovery-server:8761/eureka/"))
    and ([
      "api-gateway",
      "auth-service",
      "user-service",
      "restaurant-service",
      "order-service",
      "delivery-service",
      "search-service",
      "shipper-service",
      "settlement-service",
      "notification-service",
      "match-service",
      "tracking-service",
      "livestream-service",
      "saga-orchestrator-service",
      "promotion-service",
      "analytics-service",
      "flashsale-service"
    ] | all(. as $service |
      $root.services[$service].environment.JAVA_TOOL_OPTIONS == "-Xmx384m -Xms256m"))
    and ([
      "auth-service",
      "user-service",
      "restaurant-service",
      "order-service",
      "delivery-service",
      "search-service",
      "shipper-service",
      "settlement-service",
      "notification-service",
      "match-service",
      "tracking-service",
      "livestream-service",
      "saga-orchestrator-service",
      "promotion-service",
      "analytics-service",
      "flashsale-service"
    ] | all(. as $service | ($root.services[$service].ports // []) | length == 0))
    and ([
      "api-gateway",
      "auth-service",
      "user-service",
      "restaurant-service",
      "order-service",
      "delivery-service",
      "search-service",
      "shipper-service",
      "settlement-service",
      "notification-service",
      "match-service",
      "tracking-service",
      "livestream-service",
      "saga-orchestrator-service",
      "promotion-service",
      "analytics-service",
      "flashsale-service"
    ] | all(. as $service |
      (($root.services[$service].ports // []) | all(
        ((.published // "") | tostring) != "9090"))))
    and ([
      "api-gateway",
      "auth-service",
      "user-service",
      "restaurant-service",
      "order-service",
      "delivery-service",
      "search-service",
      "shipper-service",
      "settlement-service",
      "notification-service",
      "match-service",
      "tracking-service",
      "livestream-service",
      "saga-orchestrator-service",
      "promotion-service",
      "analytics-service",
      "flashsale-service"
    ] | all(. as $service |
      (($root.services[$service].expose // []) | map(tostring) | index("9090")) != null))
    and ([
      "api-gateway",
      "auth-service",
      "user-service",
      "restaurant-service",
      "order-service",
      "delivery-service",
      "search-service",
      "shipper-service",
      "settlement-service",
      "notification-service",
      "match-service",
      "tracking-service",
      "livestream-service",
      "saga-orchestrator-service",
      "promotion-service",
      "analytics-service",
      "flashsale-service"
    ] | all(. as $service |
      (($root.services[$service].environment // {}) | to_entries | all(
        ((.key | test("^(APP_.*_SERVICE_(URI|WS_URI)|[A-Z_]+_SERVICE_URL|SPRING_DATASOURCE_URL|SPRING_KAFKA_BOOTSTRAP_SERVERS|SPRING_DATA_REDIS_HOST|SPRING_ELASTICSEARCH_URIS)$")) | not)
        or (((.value // "") | tostring | test("localhost|127\\.0\\.0\\.1")) | not)
      ))))
' >/dev/null

printf '%s' "$rendered_rotation_config" | jq -e \
  --arg expectedRetiringKid "$JWT_RETIRING_KID" '
  (.services["api-gateway"].environment | has("JWT_PREVIOUS_PUBLIC_KEY_PATH") | not)
    and ((.services["api-gateway"].secrets // []) | map(.source) | index("jwt-previous-public-key") | not)
    and .services["auth-service"].environment.JWT_PREVIOUS_PUBLIC_KEY_PATH
      == "/run/secrets/jwt-previous-public.pem"
    and .services["auth-service"].environment.JWT_RETIRING_KID == $expectedRetiringKid
    and (.services["auth-service"].secrets | map(.source) | index("jwt-previous-public-key") != null)
' >/dev/null

printf '%s' "$rendered_secret_config" | jq -e '
  (.services["api-gateway"].environment | has("JWT_PUBLIC_KEY_PATH") | not)
    and (.services["api-gateway"].environment | has("JWT_PREVIOUS_PUBLIC_KEY_PATH") | not)
    and .services["auth-service"].environment.JWT_PRIVATE_KEY_PATH
      == "/run/secrets/jwt-private.pem"
    and .services["auth-service"].environment.JWT_PUBLIC_KEY_PATH
      == "/run/secrets/jwt-public.pem"
    and ((.services["api-gateway"].secrets // []) | length == 0)
    and (.services["auth-service"].secrets | length == 4)
    and (.services["auth-service"].environment | has("INTERNAL_SECRET") | not)
    and (.services["auth-service"].environment | has("SPRING_DATASOURCE_PASSWORD") | not)
    and (.secrets["internal-secret"] != null)
    and (.secrets["db-password"] != null)
    and (.secrets["jwt-private-key"] != null)
    and (.secrets["jwt-public-key"] != null)
' >/dev/null

# The explicit rollback must preserve the Gateway-only boundary while restoring
# only private static URLs and disabling discovery/config fail-fast for recovery.
printf '%s' "$rendered_static_route_config" | jq -e '
  . as $root
  | .services["api-gateway"].environment.SERVICE_DISCOVERY_ENABLED == "false"
    and .services["api-gateway"].environment.APP_AUTH_SERVICE_URI == "http://auth-service:8081"
    and .services["api-gateway"].environment.APP_TRACKING_SERVICE_WS_URI == "ws://tracking-service:8093"
    and .services["auth-service"].environment.USER_SERVICE_URL == "http://user-service:8082"
    and .services["restaurant-service"].environment.ORDER_SERVICE_URL == "http://order-service:8084"
    and .services["order-service"].environment.RESTAURANT_SERVICE_URL == "http://restaurant-service:8083"
    and .services["match-service"].environment.SETTLEMENT_SERVICE_URL == "http://settlement-service:8090"
    and .services["tracking-service"].environment.DELIVERY_SERVICE_URL == "http://delivery-service:8085"
    and (["api-gateway", "auth-service", "user-service", "restaurant-service", "order-service",
          "delivery-service", "search-service", "shipper-service", "settlement-service",
          "notification-service", "match-service", "tracking-service", "saga-orchestrator-service"]
      | all(. as $service | (($root.services[$service].ports // []) | length == 0)
          or $service == "api-gateway"))
' >/dev/null

# The disposable E2E overlay must never collide with an already-running local
# stack. It deliberately removes every fixed container name and infrastructure
# host port; Gateway alone receives a Docker-assigned loopback port.
printf '%s' "$rendered_isolated_e2e_config" | jq -e '
  . as $root
  | ([
    "postgres", "redis", "kafka", "elasticsearch", "api-gateway",
    "auth-service", "user-service", "restaurant-service", "order-service",
    "delivery-service", "search-service", "shipper-service", "settlement-service",
    "notification-service", "match-service", "tracking-service", "livestream-service",
    "saga-orchestrator-service", "promotion-service", "analytics-service",
    "flashsale-service", "prometheus", "grafana"
  ] | all(. as $service | ($root.services[$service].container_name // null) == null))
  and (["tracing-collector", "postgres", "redis", "kafka", "elasticsearch"]
       | all(. as $service | (($root.services[$service].ports // []) | length) == 0))
  and (($root.services["api-gateway"].ports // []) | length) == 1
  and $root.services["api-gateway"].ports[0].target == 8079
  and $root.services["api-gateway"].ports[0].host_ip == "127.0.0.1"
  and ($root.services["api-gateway"].ports[0].published // null) == null
  and $root.volumes.postgres_data.name == "delivery_e2e_contract_postgres"
  and $root.volumes.kafka_data.name == "delivery_e2e_contract_kafka"
' >/dev/null

printf '%s\n' "Compose configuration contract is valid."
