#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BOOT_VERSION="3.5.15"
CLOUD_VERSION="2025.0.3"

modules=(
  runtime-platform-starter
  discovery-server
  config-server
  auth-service
  user-service
  api-gateway
  delivery-service
  notification-service
  order-service
  restaurant-service
  shipper-service
  search-service
  saga-orchestrator-service
  tracking-service
  match-service
  livestream-service
  settlement-service
  flashsale-service
  analytics-service
  promotion-service
)

if [[ -n "${JAVA_HOME:-}" ]]; then
  java_bin="${JAVA_HOME}/bin/java"
else
  java_bin="java"
fi

java_version="$("${java_bin}" -XshowSettings:properties -version 2>&1 \
  | awk -F'= ' '/java.specification.version/{print $2; exit}')"
if [[ "${java_version}" != "17" ]]; then
  echo "Expected JDK 17, found Java specification version '${java_version:-unknown}'." >&2
  exit 1
fi

maven_version_output="$(mvn -version 2>&1)"
maven_java_version="$(printf '%s\n' "${maven_version_output}" \
  | sed -n 's/^Java version: \([^,]*\).*/\1/p' \
  | head -n 1)"
if [[ "${maven_java_version%%.*}" != "17" ]]; then
  echo "Expected Maven to run on JDK 17, found '${maven_java_version:-unknown}'. Set JAVA_HOME to JDK 17." >&2
  exit 1
fi

for module in "${modules[@]}"; do
  pom="${ROOT_DIR}/${module}/pom.xml"
  if ! rg -q -U "<artifactId>spring-boot-starter-parent</artifactId>[[:space:]]*<version>${BOOT_VERSION//./\\.}</version>" "${pom}"; then
    echo "${module}: Spring Boot parent must be ${BOOT_VERSION}." >&2
    exit 1
  fi
  if ! rg -q "<java.version>17</java.version>" "${pom}"; then
    echo "${module}: java.version must be 17." >&2
    exit 1
  fi
done

# Surefire reports are evidence only when they still correspond to an existing
# test source and are newer than that source. This catches deleted tests or
# reports left behind by a non-clean module run before suite totals are counted.
stale_surefire_reports=""
while IFS= read -r report; do
  module_dir="${report%%/target/*}"
  class_name="${report##*/TEST-}"
  class_name="${class_name%.xml}"
  class_name="${class_name%%\$*}"
  relative_test="$(printf '%s' "${class_name}" | tr '.' '/')"
  test_source="${module_dir}/src/test/java/${relative_test}.java"
  if [[ ! -f "${test_source}" || "${test_source}" -nt "${report}" ]]; then
    stale_surefire_reports+="${report}\n"
  fi
done < <(find "${ROOT_DIR}" -path '*/target/surefire-reports/TEST-*.xml' -type f -print)

if [[ -n "${stale_surefire_reports}" ]]; then
  echo "Stale Surefire reports found; run clean test before counting evidence:" >&2
  printf '%b' "${stale_surefire_reports}" >&2
  exit 1
fi

gateway_pom="${ROOT_DIR}/api-gateway/pom.xml"
if ! rg -q "<version>${CLOUD_VERSION//./\\.}</version>" "${gateway_pom}"; then
  echo "api-gateway: Spring Cloud BOM must be ${CLOUD_VERSION}." >&2
  exit 1
fi
if ! rg -q "<artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>" "${gateway_pom}"; then
  echo "api-gateway: expected the WebFlux Gateway server starter." >&2
  exit 1
fi
for platform_module in runtime-platform-starter discovery-server config-server; do
  if ! rg -q "<version>${CLOUD_VERSION//./\\.}</version>" "${ROOT_DIR}/${platform_module}/pom.xml"; then
    echo "${platform_module}: Spring Cloud BOM must be ${CLOUD_VERSION}." >&2
    exit 1
  fi
done
if ! rg -q 'spring-cloud-starter-netflix-eureka-client' "${ROOT_DIR}/runtime-platform-starter/pom.xml" \
    || ! rg -q 'spring-cloud-starter-config' "${ROOT_DIR}/runtime-platform-starter/pom.xml" \
    || ! rg -q 'spring-cloud-starter-netflix-eureka-server' "${ROOT_DIR}/discovery-server/pom.xml" \
    || ! rg -q 'spring-cloud-config-server' "${ROOT_DIR}/config-server/pom.xml"; then
  echo "Runtime platform must include Config client/server and Eureka client/server dependencies." >&2
  exit 1
fi

if rg -q "<version>(2022|2023)\\.0\\.[0-9]+</version>" "${ROOT_DIR}"/*/pom.xml; then
  echo "Unsupported Spring Cloud 2022/2023 BOM remains in a module POM." >&2
  exit 1
fi

if rg -q 'org\.mapstruct|mapstruct-processor|lombok-mapstruct-binding' \
    "${ROOT_DIR}"/*/pom.xml "${ROOT_DIR}"/*/src/main/java; then
  echo "MapStruct generated mappers are not allowed: clean builds must use deterministic source mappers." >&2
  exit 1
fi

tracking_root="${ROOT_DIR}/tracking-service"
tracking_proto_files="$(rg --files "${tracking_root}/src/main" 2>/dev/null \
  | rg '/proto/|\.proto$' || true)"
if [[ -n "${tracking_proto_files}" ]] \
    || rg -qi 'io\.grpc|grpc-|protobuf-maven-plugin|protobuf-java' \
      "${tracking_root}/pom.xml" "${tracking_root}/src/main/java"; then
  echo "tracking-service: gRPC/protobuf runtime artifacts are outside the raw-WebSocket MVP contract." >&2
  exit 1
fi

auth_service_file="${ROOT_DIR}/auth-service/src/main/java/com/delivery/auth_service/service/AuthService.java"
auth_operator_shipper_runner="${ROOT_DIR}/auth-service/src/main/java/com/delivery/auth_service/runner/OperatorShipperProvisioningRunner.java"
auth_operator_admin_runner="${ROOT_DIR}/auth-service/src/main/java/com/delivery/auth_service/runner/OperatorAdminProvisioningRunner.java"
auth_operator_admin_script="${ROOT_DIR}/scripts/operator-provision-admin.sh"
auth_properties="${ROOT_DIR}/auth-service/src/main/resources/application.properties"
if [[ ! -f "${auth_operator_shipper_runner}" ]] \
    || ! rg -Fq 'operatorProvisionShipperAccount' "${auth_service_file}" \
    || ! rg -Fq 'parsePublicRegistrationRole' "${auth_service_file}" \
    || ! rg -Fq 'parsed == AuthAccount.Role.SHIPPER' "${auth_service_file}" \
    || ! rg -Fq 'APP_OPERATOR_SHIPPER_PROVISIONING_ENABLED' "${auth_properties}" \
    || ! rg -Fq 'APP_OPERATOR_SHIPPER_PROVISIONING_EXIT_AFTER_RUN' "${auth_properties}" \
    || ! rg -Fq 'authService.operatorProvisionShipperAccount' "${auth_operator_shipper_runner}"; then
  echo "auth-service: SHIPPER fixtures must use explicit operator provisioning while public registration remains closed." >&2
  exit 1
fi
if rg -n 'operatorProvisionAdmin|APP_OPERATOR_ADMIN|AuthAccount\.Role\.ADMIN' \
    "${auth_operator_shipper_runner}" >/dev/null; then
  echo "auth-service: SHIPPER fixture runner must not create or manage ADMIN accounts." >&2
  exit 1
fi
if [[ ! -f "${auth_operator_admin_runner}" ]] \
    || [[ ! -f "${auth_operator_admin_script}" ]] \
    || ! rg -Fq 'operatorProvisionAdminAccount' "${auth_service_file}" \
    || ! rg -Fq 'APP_OPERATOR_ADMIN_PROVISIONING_ENABLED' "${auth_properties}" \
    || ! rg -Fq 'APP_OPERATOR_ADMIN_PROVISIONING_EMAIL' "${auth_properties}" \
    || ! rg -Fq 'APP_OPERATOR_ADMIN_PROVISIONING_PASSWORD' "${auth_properties}" \
    || ! rg -Fq 'APP_OPERATOR_ADMIN_PROVISIONING_EXIT_AFTER_RUN' "${auth_properties}" \
    || ! rg -Fq 'authService.operatorProvisionAdminAccount' "${auth_operator_admin_runner}" \
    || ! rg -Fq 'APP_OPERATOR_ADMIN_PROVISIONING_ENABLED=true' "${auth_operator_admin_script}" \
    || ! rg -Fq 'APP_OPERATOR_ADMIN_PROVISIONING_EMAIL="$ADMIN_EMAIL"' "${auth_operator_admin_script}" \
    || ! rg -Fq 'APP_OPERATOR_ADMIN_PROVISIONING_PASSWORD="$ADMIN_PASSWORD"' "${auth_operator_admin_script}"; then
  echo "auth-service: ADMIN fixture must use explicit operator provisioning and must not rely on public registration or SQL patching." >&2
  exit 1
fi
if rg -n 'operatorProvisionShipper|APP_OPERATOR_SHIPPER|AuthAccount\.Role\.SHIPPER' \
    "${auth_operator_admin_runner}" >/dev/null; then
  echo "auth-service: ADMIN fixture runner must not create or manage SHIPPER accounts." >&2
  exit 1
fi
if rg -n '/api/auth/register|psql|INSERT INTO auth_account|UPDATE auth_account' \
    "${auth_operator_admin_script}" >/dev/null; then
  echo "auth-service: ADMIN fixture script must not use public registration or SQL patching." >&2
  exit 1
fi
if rg -n 'role\\":\\"SHIPPER|\"role\"[[:space:]]*:[[:space:]]*\"SHIPPER\"|register .*ROLE_SHIPPER|ROLE_SHIPPER.*register' \
    "${ROOT_DIR}/scripts/seed.sh" \
    "${ROOT_DIR}/scripts/verify-mvp-cod-flow.sh" \
    "${ROOT_DIR}/scripts/verify-mvp-failure-matrix.sh" \
    "${ROOT_DIR}/scripts/verify-clean-compose-e2e.sh" >/dev/null; then
  echo "runtime harnesses must not self-register SHIPPER through public /api/auth/register." >&2
  exit 1
fi

java_without_package="$(rg --files-without-match '^package ' \
  "${ROOT_DIR}"/*/src/main/java -g '*.java' || true)"
if [[ -n "${java_without_package}" ]]; then
  echo "Java source files without a package declaration remain:" >&2
  printf '%s\n' "${java_without_package}" >&2
  exit 1
fi

if rg -n -U '@Autowired[[:space:]]*(private|protected|public)?[[:space:]]+[^()\n]+;' \
    "${ROOT_DIR}"/*/src/main/java >/dev/null; then
  echo "Production source must not use @Autowired field injection." >&2
  exit 1
fi

# VNPay IPN is an external provider callback whose acknowledgement shape is provider-owned.
# JWKS is an RFC-defined discovery document and intentionally is not wrapped in
# the product BaseResponse envelope.
raw_controller_responses="$(rg -n 'public ResponseEntity<' \
  --glob '**/src/main/java/**/*Controller.java' "${ROOT_DIR}" \
  | rg -v 'BaseResponse' \
  | rg -v '/PaymentController\.java:' \
  | rg -v '/JwksController\.java:' \
  || true)"
if [[ -n "${raw_controller_responses}" ]]; then
  echo "Public controllers must use the canonical BaseResponse envelope:" >&2
  printf '%s\n' "${raw_controller_responses}" >&2
  exit 1
fi

if rg -n -U 'BaseResponse\(int status,[[:space:]]*String message,[[:space:]]*T data\)' \
    --glob '**/src/main/java/**/BaseResponse.java' "${ROOT_DIR}" >/dev/null; then
  echo "BaseResponse three-argument constructors must use (status, data, message); prefer named factories." >&2
  exit 1
fi

if rg -n 'new BaseResponse<>\(HttpStatus\.[A-Z_]+\.value\(\)' \
    --glob '**/src/main/java/**/*.java' "${ROOT_DIR}" >/dev/null; then
  echo "BaseResponse.status is a 1/0 contract flag and must not contain an HTTP status code." >&2
  exit 1
fi

if rg -n 'new BaseResponse<>' \
    --glob '!**/BaseResponse.java' \
    "${ROOT_DIR}/auth-service/src/main/java" \
    "${ROOT_DIR}/settlement-service/src/main/java" \
    "${ROOT_DIR}/flashsale-service/src/main/java" >/dev/null; then
  echo "Auth, Settlement and Flash Sale must use BaseResponse named factories at call sites." >&2
  exit 1
fi

if rg -n 'log\.(error|warn|info)\([^\n]*(Raw Message|Raw:|rawPayload|message\s*\+)' \
    "${ROOT_DIR}/notification-service/src/main/java" >/dev/null; then
  echo "Notification Kafka listeners must not log raw event payloads." >&2
  exit 1
fi

if rg -n 'Math\.random\(|Featured Item|example\.com/item|RestaurantCatalog(Service|Response)' \
    "${ROOT_DIR}/restaurant-service/src/main/java" >/dev/null; then
  echo "restaurant-service: synthetic/random catalog data or its dead cache graph must not return." >&2
  exit 1
fi

if rg -n 'estimateShipperEarnings|Missing coordinates, using minimum shipping fee' \
    "${ROOT_DIR}/order-service/src/main/java" >/dev/null; then
  echo "order-service: non-canonical shipping fallback or dead 80% earnings policy must not return." >&2
  exit 1
fi

if rg -n 'getRestaurantName.*"Nhà hàng"' \
    "${ROOT_DIR}/notification-service/src/main/java" >/dev/null; then
  echo "notification-service: canonical restaurant names must not fall back to a synthetic label." >&2
  exit 1
fi

if rg -n 'ResponseEntity<(BaseResponse<)?Page<' \
    --glob '**/src/main/java/**/*Controller.java' "${ROOT_DIR}" >/dev/null; then
  echo "Public pagination must use the stable PageResponse DTO, not Spring Page serialization." >&2
  exit 1
fi

for stable_page in \
  "${ROOT_DIR}/order-service/src/main/java/com/delivery/order_service/payload/PageResponse.java" \
  "${ROOT_DIR}/shipper-service/src/main/java/com/delivery/shipper_service/payload/PageResponse.java" \
  "${ROOT_DIR}/search-service/src/main/java/com/delivery/search_service/payload/PageResponse.java"; do
  if ! rg -q -U 'List<T> items,[[:space:]]*int page,[[:space:]]*int size,[[:space:]]*long totalItems,[[:space:]]*int totalPages,[[:space:]]*boolean hasNext' \
      "${stable_page}"; then
    echo "Stable pagination contract drifted in ${stable_page}." >&2
    exit 1
  fi
done

if rg -q 'spring\.jpa\.show-sql=true|spring\.jpa\.properties\.hibernate\.format_sql=true|logging\.level\.[^=]+=(DEBUG|TRACE)' \
    "${ROOT_DIR}"/*/src/main/resources/application*.properties; then
  echo "Unsafe verbose logging is enabled by default in a main application properties file." >&2
  exit 1
fi
if rg -q 'show-sql:[[:space:]]*true|format_sql:[[:space:]]*true|:[[:space:]]*(DEBUG|TRACE)[[:space:]]*$' \
    "${ROOT_DIR}"/*/src/main/resources/application*.yml; then
  echo "Unsafe verbose logging is enabled by default in a main application YAML file." >&2
  exit 1
fi
if rg -q 'spring\.datasource\.password=123456|password:[[:space:]]*123456' \
    "${ROOT_DIR}"/*/src/main/resources/application*; then
  echo "A main application config still contains the legacy default database password." >&2
  exit 1
fi

hidden_capability_defaults=(
  'analytics-service/src/main/resources/application.properties|app.analytics.processing-enabled=${ANALYTICS_PROCESSING_ENABLED:false}'
  'flashsale-service/src/main/resources/application.properties|app.flashsale.checkout-enabled=${FLASHSALE_CHECKOUT_ENABLED:false}'
  'flashsale-service/src/main/resources/application.properties|app.flashsale.outbox-relay-enabled=${FLASHSALE_OUTBOX_RELAY_ENABLED:false}'
  'flashsale-service/src/main/resources/application.properties|app.flashsale.merchant-registration-enabled=${FLASHSALE_MERCHANT_REGISTRATION_ENABLED:false}'
  'livestream-service/src/main/resources/application.properties|app.livestream.api-enabled=${LIVESTREAM_API_ENABLED:false}'
  'order-service/src/main/resources/application.properties|app.order.payment-event-processing-enabled=${ORDER_PAYMENT_EVENT_PROCESSING_ENABLED:false}'
  'order-service/src/main/resources/application.properties|app.order.voucher-checkout-enabled=${ORDER_VOUCHER_CHECKOUT_ENABLED:false}'
  'order-service/src/main/resources/application.properties|app.order.flashsale-checkout-enabled=${ORDER_FLASHSALE_CHECKOUT_ENABLED:false}'
  'settlement-service/src/main/resources/application.properties|app.payment.processing-enabled=${PAYMENT_PROCESSING_ENABLED:false}'
  'settlement-service/src/main/resources/application.properties|app.payment.fake-provider-enabled=${FAKE_PAYMENT_PROVIDER_ENABLED:false}'
  'settlement-service/src/main/resources/application.properties|app.settlement.self-service-api-enabled=${SETTLEMENT_SELF_SERVICE_API_ENABLED:false}'
  'settlement-service/src/main/resources/application.properties|app.settlement.admin-mutation-api-enabled=${SETTLEMENT_ADMIN_MUTATION_API_ENABLED:false}'
  'shipper-service/src/main/resources/application.properties|app.shipper.legacy-rating-write-api-enabled=${SHIPPER_LEGACY_RATING_WRITE_API_ENABLED:false}'
  'shipper-service/src/main/resources/application.properties|app.shipper.legacy-delete-api-enabled=${SHIPPER_LEGACY_DELETE_API_ENABLED:false}'
  'promotion-service/src/main/resources/application.yml|merchant-create-api-enabled: ${PROMOTION_MERCHANT_CREATE_API_ENABLED:false}'
  'promotion-service/src/main/resources/application.yml|checkout-enabled: ${PROMOTION_CHECKOUT_ENABLED:false}'
  'promotion-service/src/main/resources/application.yml|outbox-relay-enabled: ${PROMOTION_OUTBOX_RELAY_ENABLED:false}'
)
for entry in "${hidden_capability_defaults[@]}"; do
  relative_file="${entry%%|*}"
  expected="${entry#*|}"
  if ! rg -Fq "${expected}" "${ROOT_DIR}/${relative_file}"; then
    echo "${relative_file}: hidden capability must remain explicitly off by default (${expected})." >&2
    exit 1
  fi
done

for fake_payment_source in \
  "${ROOT_DIR}/settlement-service/src/main/java/com/delivery/settlement_service/controller/FakePaymentController.java" \
  "${ROOT_DIR}/settlement-service/src/main/java/com/delivery/settlement_service/payment/provider/FakePaymentProvider.java"; do
  if ! rg -Fq '@Profile({"dev", "test"})' "${fake_payment_source}"; then
    echo "Settlement fake-payment beans must remain isolated to dev/test profiles." >&2
    exit 1
  fi
done
legacy_order_read_controller="${ROOT_DIR}/order-service/src/main/java/com/delivery/order_service/controller/LegacyOrderReadController.java"
legacy_order_mutation_controller="${ROOT_DIR}/order-service/src/main/java/com/delivery/order_service/controller/LegacyOrderMutationController.java"
legacy_order_properties="${ROOT_DIR}/order-service/src/main/resources/application.properties"
if [[ -e "${legacy_order_read_controller}" || -e "${legacy_order_mutation_controller}" ]]; then
  echo "order-service: legacy order controllers must remain deleted." >&2
  exit 1
fi
if rg -Fq 'ORDER_LEGACY_' "${legacy_order_properties}" \
    || rg -Fq 'app.order.legacy-' "${legacy_order_properties}"; then
  echo "order-service: legacy order capability flags must remain removed." >&2
  exit 1
fi
if rg -q 'BaseResponse<(java\.util\.List<)?Voucher>' \
    "${ROOT_DIR}/promotion-service/src/main/java/com/delivery/promotion_service/controller"; then
  echo "promotion-service: HTTP controllers must return VoucherResponse instead of serializing Voucher entities." >&2
  exit 1
fi
if rg -q 'PageResponse<(Restaurant|Dish|Shipper)Document>' \
    "${ROOT_DIR}/search-service/src/main/java/com/delivery/search_service/controller"; then
  echo "search-service: HTTP controllers must return search DTOs instead of Elasticsearch documents." >&2
  exit 1
fi
if rg -q 'private[[:space:]]+Double[[:space:]]+price;' \
    "${ROOT_DIR}/search-service/src/main/java/com/delivery/search_service"; then
  echo "search-service: dish price must use BigDecimal, not Double." >&2
  exit 1
fi
if rg -q 'Shipper(SearchController|SearchRepository|Document|SearchResponse)|searchShippers|SHIPPER_SEARCH_SYNC|SEARCH_SHIPPER_' \
    "${ROOT_DIR}/search-service/src" \
    "${ROOT_DIR}/shipper-service/src" \
    "${ROOT_DIR}/docker-compose.yml"; then
  echo "Dead shipper Elasticsearch search/sync graph must not be restored without product authority and a caller." >&2
  exit 1
fi
unsafe_match_if_missing="$(rg -l 'matchIfMissing[[:space:]]*=[[:space:]]*true' \
  --glob '*.java' "${ROOT_DIR}"/*/src/main/java \
  | rg -v '/(OrderOutboxRelay|RestaurantOutboxRelay|SagaOutboxRelay|OutboxMessageRelay)\.java$' || true)"
if [[ -n "${unsafe_match_if_missing}" ]]; then
  echo "Hidden/optional components must not use matchIfMissing=true:" >&2
  printf '%s\n' "${unsafe_match_if_missing}" >&2
  exit 1
fi

auth_properties="${ROOT_DIR}/auth-service/src/main/resources/application.properties"
auth_token_service="${ROOT_DIR}/auth-service/src/main/java/com/delivery/auth_service/service/TokenService.java"
if ! rg -Fq 'jwt.private-key.path=${JWT_PRIVATE_KEY_PATH:}' "${auth_properties}" \
    || ! rg -Fq 'jwt.public-key.path=${JWT_PUBLIC_KEY_PATH:}' "${auth_properties}" \
    || rg -q '@Value\("\$\{jwt\.(private|public)-key\.path:classpath:' \
      "${auth_token_service}"; then
  echo "Auth JWT key locations must be env-backed and blank-by-default so startup fails fast without mounted keys." >&2
  exit 1
fi
if ! rg -Fq 'REFRESH_TOKEN_TTL = Duration.ofDays(7)' "${auth_token_service}" \
    || ! rg -Fq 'jwt.access-token-ttl-seconds=${JWT_ACCESS_TOKEN_TTL_SECONDS:900}' "${auth_properties}" \
    || rg -q '24[[:space:]]*\*[[:space:]]*7[[:space:]]*\*[[:space:]]*10|100 days for debug|Duration\.ofDays\(100\)' \
      "${auth_token_service}"; then
  echo "auth-service: access/refresh JWT expiry must match the confirmed 15-minute/7-day authority." >&2
  exit 1
fi
if rg -q 'System\.getenv\("JWT_(PRIVATE|PUBLIC)_KEY_PATH"\)' \
    "${ROOT_DIR}/auth-service/src/main/java" \
    "${ROOT_DIR}/api-gateway/src/main/java"; then
  echo "JWT loaders must use Spring-configured key locations instead of bypassing configuration." >&2
  exit 1
fi

jpa_modules=(
  auth-service user-service delivery-service notification-service order-service
  restaurant-service shipper-service saga-orchestrator-service livestream-service
  settlement-service flashsale-service analytics-service promotion-service
)
for module in "${jpa_modules[@]}"; do
  resources="${ROOT_DIR}/${module}/src/main/resources"
  if ! rg -q 'spring\.jpa\.open-in-view=false|open-in-view:[[:space:]]*false' \
      "${resources}"/application.*; then
    echo "${module}: spring.jpa.open-in-view must be false." >&2
    exit 1
  fi
done

flyway_authority_modules=(
  auth-service user-service notification-service order-service restaurant-service shipper-service
  flashsale-service analytics-service livestream-service delivery-service
  saga-orchestrator-service settlement-service
)
for module in "${flyway_authority_modules[@]}"; do
  properties="${ROOT_DIR}/${module}/src/main/resources/application.properties"
  if ! rg -q '^spring\.jpa\.hibernate\.ddl-auto=validate$' "${properties}"; then
    echo "${module}: Flyway-owned production schema must use Hibernate validate." >&2
    exit 1
  fi
  if ! rg -q '^spring\.flyway\.enabled=true$' "${properties}"; then
    echo "${module}: Flyway must remain enabled for its production schema." >&2
    exit 1
  fi
done

promotion_config="${ROOT_DIR}/promotion-service/src/main/resources/application.yml"
if ! rg -q 'ddl-auto:[[:space:]]*validate' "${promotion_config}"; then
  echo "promotion-service: Flyway-owned production schema must use Hibernate validate." >&2
  exit 1
fi
if ! rg -q 'enabled:[[:space:]]*true' "${promotion_config}"; then
  echo "promotion-service: Flyway must remain enabled for its production schema." >&2
  exit 1
fi

if [[ -e "${ROOT_DIR}/delivery-service/src/main/resources/schema.sql" ]]; then
  echo "delivery-service: schema.sql must not duplicate Flyway-owned tables." >&2
  exit 1
fi
if rg -q 'ALTER TABLE[[:space:]]+deliveries' \
    "${ROOT_DIR}/delivery-service/src/main/java"; then
  echo "delivery-service: runtime Java code must not mutate the Flyway-owned schema." >&2
  exit 1
fi

order_kafka_config="${ROOT_DIR}/order-service/src/main/java/com/delivery/order_service/config/KafkaConfig.java"
if ! rg -Fq 'ownerDltTopic(record.topic())' "${order_kafka_config}" \
    || ! rg -Fq 'replaceFirst("-retry-order-\\d+$", "") + ".order.DLT"' "${order_kafka_config}" \
    || ! rg -Fq 'new FixedBackOff(1000L, 2)' "${order_kafka_config}" \
    || ! rg -Fq 'recoverer.setFailIfSendResultIsError(true)' "${order_kafka_config}"; then
  echo "order-service: Kafka consumer failures must use finite retry and fail-closed owner-isolated same-partition DLT recovery." >&2
  exit 1
fi

delivery_kafka_config="${ROOT_DIR}/delivery-service/src/main/java/com/delivery/delivery_service/config/KafkaConfig.java"
if ! rg -Fq 'record.topic() + ".DLT"' "${delivery_kafka_config}" \
    || ! rg -Fq 'new FixedBackOff(1000L, 2)' "${delivery_kafka_config}" \
    || ! rg -Fq 'recoverer.setFailIfSendResultIsError(true)' "${delivery_kafka_config}"; then
  echo "delivery-service: Kafka command failures must use finite retry and fail-closed same-partition DLT recovery." >&2
  exit 1
fi

delivery_pom="${ROOT_DIR}/delivery-service/pom.xml"
delivery_main="${ROOT_DIR}/delivery-service/src/main/java"
if rg -Fq '<artifactId>spring-boot-starter-websocket</artifactId>' "${delivery_pom}" \
    || rg -n 'EnableWebSocketMessageBroker|SimpMessagingTemplate|/ws/delivery-native|DeliveryWebSocketService' \
      "${delivery_main}" >/dev/null; then
  echo "delivery-service: STOMP/WebSocket graph must remain removed; status uses REST/Kafka and location uses Tracking raw WebSocket." >&2
  exit 1
fi

notification_kafka_config="${ROOT_DIR}/notification-service/src/main/java/com/delivery/notification_service/config/KafkaConfig.java"
if ! rg -Fq 'ownerDltTopic(record.topic())' "${notification_kafka_config}" \
    || ! rg -Fq 'replaceFirst("-retry-notification-\\d+$", "") + ".notification.DLT"' "${notification_kafka_config}" \
    || ! rg -Fq 'new FixedBackOff(1000L, 2)' "${notification_kafka_config}" \
    || ! rg -Fq 'recoverer.setFailIfSendResultIsError(true)' "${notification_kafka_config}"; then
  echo "notification-service: Kafka event failures must use finite retry and fail-closed owner-isolated same-partition DLT recovery." >&2
  exit 1
fi

notification_repository="${ROOT_DIR}/notification-service/src/main/java/com/delivery/notification_service/repository/NotificationRepository.java"
notification_service_impl="${ROOT_DIR}/notification-service/src/main/java/com/delivery/notification_service/service/impl/NotificationServiceImpl.java"
if ! rg -Fq 'ON CONFLICT (deduplication_key) DO NOTHING' "${notification_repository}" \
    || ! rg -Fq '@Transactional(propagation = Propagation.REQUIRES_NEW)' "${notification_repository}" \
    || ! rg -Fq 'int insertIfAbsentPostgres(' "${notification_repository}" \
    || ! rg -Fq 'int inserted = insertIfAbsent(notification);' "${notification_service_impl}" \
    || ! rg -Fq 'return notificationRepository.insertIfAbsentPostgres(' "${notification_service_impl}" \
    || ! rg -Fq 'assertReplayMatches(saved, request);' "${notification_service_impl}" \
    || ! rg -Fq 'if (request.getDeduplicationKey() == null || request.getDeduplicationKey().isBlank())' "${notification_service_impl}"; then
  echo "notification-service: keyed Kafka notifications must atomically commit one durable PENDING row before external delivery." >&2
  exit 1
fi

saga_kafka_config="${ROOT_DIR}/saga-orchestrator-service/src/main/java/com/delivery/saga_orchestrator_service/config/KafkaConfig.java"
if ! rg -Fq 'ownerDltTopic(record.topic())' "${saga_kafka_config}" \
    || ! rg -Fq 'replaceFirst("-retry-saga-\\d+$", "") + ".saga.DLT"' "${saga_kafka_config}" \
    || ! rg -Fq 'new FixedBackOff(1000L, 2)' "${saga_kafka_config}" \
    || ! rg -Fq 'recoverer.setFailIfSendResultIsError(true)' "${saga_kafka_config}"; then
  echo "saga-orchestrator-service: Kafka command failures must use finite retry and fail-closed owner-isolated same-partition DLT recovery." >&2
  exit 1
fi

notification_pom="${ROOT_DIR}/notification-service/pom.xml"
notification_main="${ROOT_DIR}/notification-service/src/main/java"
if rg -Fq '<artifactId>spring-boot-starter-websocket</artifactId>' "${notification_pom}" \
    || rg -n 'EnableWebSocketMessageBroker|SimpMessagingTemplate|/ws-native|sendWebSocket' \
      "${notification_main}" >/dev/null; then
  echo "notification-service: STOMP/WebSocket graph must remain removed; MVP realtime is Tracking raw location only." >&2
  exit 1
fi

match_kafka_config="${ROOT_DIR}/match-service/src/main/java/com/delivery/match_service/config/KafkaConfig.java"
if ! rg -Fq 'record.topic() + ".DLT"' "${match_kafka_config}" \
    || ! rg -Fq 'new FixedBackOff(1000L, 3)' "${match_kafka_config}" \
    || ! rg -Fq 'recoverer.setFailIfSendResultIsError(true)' "${match_kafka_config}"; then
  echo "match-service: Kafka projection failures must use finite retry and fail-closed same-partition DLT recovery." >&2
  exit 1
fi

for core_kafka_config in \
  "${ROOT_DIR}/settlement-service/src/main/java/com/delivery/settlement_service/config/KafkaConsumerConfig.java"; do
  if ! rg -Fq 'record.topic() + ".DLT"' "${core_kafka_config}" \
      || ! rg -Fq 'new FixedBackOff(1000L, 2)' "${core_kafka_config}" \
      || ! rg -Fq 'recoverer.setFailIfSendResultIsError(true)' "${core_kafka_config}"; then
    echo "$(basename "$(dirname "$(dirname "$(dirname "$(dirname "${core_kafka_config}")")")")"): core Kafka consumer recovery policy is not fail-closed." >&2
    exit 1
  fi
done

livestream_repository="${ROOT_DIR}/livestream-service/src/main/java/com/delivery/livestream_service/repository/LivestreamRepository.java"
livestream_product_repository="${ROOT_DIR}/livestream-service/src/main/java/com/delivery/livestream_service/repository/LivestreamProductRepository.java"
analytics_event_repository="${ROOT_DIR}/analytics-service/src/main/java/com/delivery/analytics_service/repository/AnalyticsEventRepository.java"
if [[ "$(rg -c 'Pageable pageable' "${livestream_repository}")" -ne 3 ]] \
    || [[ "$(rg -c 'Pageable pageable' "${livestream_product_repository}")" -ne 2 ]] \
    || ! rg -Fq 'Page<AnalyticsEvent> findByEventTimeBetween' "${analytics_event_repository}"; then
  echo "hidden capability list/reconciliation queries must remain bounded or paged." >&2
  exit 1
fi
if [[ -e "${ROOT_DIR}/livestream-service/src/main/java/com/delivery/livestream_service/repository/LivestreamEventRepository.java" ]] \
    || rg -q 'findByEntityIdAndEntityTypeOrderByCreatedAtDesc' \
      "${ROOT_DIR}/settlement-service/src/main/java/com/delivery/settlement_service/repository/PaymentOrderRepository.java" \
    || rg -q 'findByRoomId|countActiveDeliveriesByShipper' \
      "${livestream_repository}" \
      "${ROOT_DIR}/delivery-service/src/main/java/com/delivery/delivery_service/repository/DeliveryRepository.java" \
    || rg -q 'getShipperLocation' \
      "${ROOT_DIR}/tracking-service/src/main/java/com/delivery/tracking_service/service/ShipperLocationService.java"; then
  echo "dead hidden-capability repository graphs must not be restored without a caller and contract." >&2
  exit 1
fi

tracking_lease_repository="${ROOT_DIR}/tracking-service/src/main/java/com/delivery/tracking_service/repository/ShipperPublisherLeaseRepository.java"
tracking_expiry_sweeper="${ROOT_DIR}/tracking-service/src/main/java/com/delivery/tracking_service/service/PublisherLeaseExpirySweeper.java"
tracking_properties="${ROOT_DIR}/tracking-service/src/main/resources/application.properties"
if ! rg -Fq "redis.call('INCR', KEYS[1])" "${tracking_lease_repository}" \
    || ! rg -Fq 'releaseForGraceIfCurrent' "${tracking_lease_repository}" \
    || ! rg -Fq 'claimIfExpired' "${tracking_lease_repository}" \
    || ! rg -Fq 'shouldMarkOfflineAfterGrace' "${tracking_lease_repository}" \
    || ! rg -Fq 'tracking:publisher:deadlines' "${tracking_lease_repository}" \
    || ! rg -Fq "redis.call('ZRANGEBYSCORE'" "${tracking_lease_repository}" \
    || ! rg -Fq "tonumber(score) ~= tonumber(ARGV[2])" "${tracking_lease_repository}" \
    || ! rg -Fq 'shouldMarkOfflineAfterGrace(claim.lease())' "${tracking_expiry_sweeper}" \
    || ! rg -Fq 'availabilityService.markOffline(claim.lease().shipperId())' "${tracking_expiry_sweeper}" \
    || ! rg -Fq 'disconnect-grace-seconds=${TRACKING_PUBLISHER_DISCONNECT_GRACE_SECONDS:30}' "${tracking_properties}" \
    || ! rg -Fq 'lease-ttl-seconds=${TRACKING_PUBLISHER_LEASE_TTL_SECONDS:120}' "${tracking_properties}" \
    || ! rg -Fq 'expiry-sweep-interval-ms=${TRACKING_PUBLISHER_EXPIRY_SWEEP_INTERVAL_MS:5000}' "${tracking_properties}" \
    || ! rg -Fq 'expiry-sweep-batch-size=${TRACKING_PUBLISHER_EXPIRY_SWEEP_BATCH_SIZE:100}' "${tracking_properties}" \
    || ! rg -Fq 'expiry-claim-seconds=${TRACKING_PUBLISHER_EXPIRY_CLAIM_SECONDS:30}' "${tracking_properties}"; then
  echo "tracking-service: publisher fencing, disconnect grace and crash-expiry reconciliation are required." >&2
  exit 1
fi
for core_consumer in delivery-service saga-orchestrator-service match-service; do
  if rg -q 'AUTO_OFFSET_RESET_CONFIG, "latest"|auto-offset-reset=latest' \
      "${ROOT_DIR}/${core_consumer}/src/main"; then
    echo "${core_consumer}: durable core consumers must replay from earliest when group state is absent." >&2
    exit 1
  fi
done

if [[ ! -f "${ROOT_DIR}/kafka-operations-tool/src/main/java/com/delivery/kafka_operations/DltReplayApplication.java" \
    || ! -x "${ROOT_DIR}/scripts/replay-kafka-dlt-record.sh" \
    || ! -x "${ROOT_DIR}/scripts/test-replay-kafka-dlt-record.sh" ]]; then
  echo "Kafka DLT recovery must retain the guarded single-record operator tool and wrapper." >&2
  exit 1
fi
if ! rg -Fq 'DLT_REPLAY_CONFIRMATION must exactly equal' \
      "${ROOT_DIR}/kafka-operations-tool/src/main/java/com/delivery/kafka_operations/DltReplayApplication.java" \
    || ! rg -Fq 'defaults to dry-run' \
      "${ROOT_DIR}/kafka-operations-tool/src/main/java/com/delivery/kafka_operations/DltReplayApplication.java" \
    || ! rg -Fq 'has no bulk mode' "${ROOT_DIR}/docs/runbooks/resilience-operations.md"; then
  echo "Kafka DLT recovery must remain coordinate-confirmed, dry-run by default, and single-record only." >&2
  exit 1
fi
for manual_dlt_consumer in delivery-service saga-orchestrator-service match-service order-service notification-service promotion-service; do
  if ! rg -Fq 'setCommitRecovered(true)' \
      "${ROOT_DIR}/${manual_dlt_consumer}/src/main/java"; then
    echo "${manual_dlt_consumer}: manual-immediate DLT recovery must commit the recovered source offset." >&2
    exit 1
  fi
done
if rg -q '@KafkaListener\([^)]*groupId\s*=\s*"settlement-service-group"' \
    "${ROOT_DIR}/settlement-service/src/main/java"; then
  echo "settlement-service: consumer group must come from spring.kafka.consumer.group-id for isolated recovery." >&2
  exit 1
fi
if ! rg -Fq '@KafkaListener(topics = "${app.kafka.topics.delivery-completed:delivery.completed}")' \
    "${ROOT_DIR}/settlement-service/src/main/java/com/delivery/settlement_service/listener/DeliveryCompletedEventListener.java"; then
  echo "settlement-service: delivery.completed topic must keep an overridable recovery boundary." >&2
  exit 1
fi
settlement_receipt_repository="${ROOT_DIR}/settlement-service/src/main/java/com/delivery/settlement_service/repository/SettlementReceiptRepository.java"
settlement_completed_listener="${ROOT_DIR}/settlement-service/src/main/java/com/delivery/settlement_service/listener/DeliveryCompletedEventListener.java"
if ! rg -Fq 'ON CONFLICT (event_id) DO NOTHING' "${settlement_receipt_repository}" \
    || ! rg -Fq 'insertIfAbsentPostgres' "${settlement_completed_listener}" \
    || rg -Fq 'saveAndFlush(SettlementReceipt.builder()' "${settlement_completed_listener}"; then
  echo "settlement-service: delivery.completed must use an atomic receipt claim before financial postings." >&2
  exit 1
fi
refund_case_repository="${ROOT_DIR}/settlement-service/src/main/java/com/delivery/settlement_service/repository/RefundCaseRepository.java"
refund_case_service="${ROOT_DIR}/settlement-service/src/main/java/com/delivery/settlement_service/service/RefundCaseService.java"
if ! rg -Fq 'ON CONFLICT DO NOTHING' "${refund_case_repository}" \
    || ! rg -Fq 'insertIfAbsentPostgres' "${refund_case_service}" \
    || rg -Fq 'saveAndFlush(refundCase)' "${refund_case_service}"; then
  echo "settlement-service: feature-gated refund intake must atomically claim its durable case before an outbox handoff." >&2
  exit 1
fi
promotion_kafka_config="${ROOT_DIR}/promotion-service/src/main/java/com/delivery/promotion_service/config/KafkaConfig.java"
promotion_order_listener="${ROOT_DIR}/promotion-service/src/main/java/com/delivery/promotion_service/listener/OrderReservationEventListener.java"
promotion_order_processor="${ROOT_DIR}/promotion-service/src/main/java/com/delivery/promotion_service/service/PromotionOrderReservationEventProcessor.java"
promotion_order_receipt_repository="${ROOT_DIR}/promotion-service/src/main/java/com/delivery/promotion_service/repository/PromotionOrderReservationReceiptRepository.java"
promotion_order_receipt_migration="${ROOT_DIR}/promotion-service/src/main/resources/db/migration/V4__promotion_order_reservation_receipts.sql"
if [[ ! -f "${promotion_order_receipt_migration}" ]] \
    || ! rg -Fq 'promotion_order_reservation_receipts' "${promotion_order_receipt_migration}" \
    || ! rg -Fq 'ON CONFLICT (event_id) DO NOTHING' "${promotion_order_receipt_repository}" \
    || ! rg -Fq 'insertIfAbsentPostgres' "${promotion_order_processor}" \
    || ! rg -Fq '@Transactional' "${promotion_order_processor}" \
    || ! rg -Fq 'PromotionOrderReservationEventProcessor' "${promotion_order_listener}" \
    || ! rg -Fq 'retryTopicSuffix = "-retry-promotion"' "${promotion_order_listener}" \
    || ! rg -Fq 'dltTopicSuffix = ".promotion.DLT"' "${promotion_order_listener}" \
    || ! rg -Fq 'ownerDltTopic(record.topic())' "${promotion_kafka_config}" \
    || ! rg -Fq 'replaceFirst("-retry-promotion-\\d+$", "") + ".promotion.DLT"' "${promotion_kafka_config}" \
    || ! rg -Fq 'recoverer.setFailIfSendResultIsError(true)' "${promotion_kafka_config}"; then
  echo "promotion-service: feature-gated reservation events must atomically receipt/fingerprint before a commit or release and recover through owner-isolated retry/DLT topics." >&2
  exit 1
fi
if ! rg -Fq 'factory.setAutoStartup(listenerAutoStartup)' \
    "${ROOT_DIR}/delivery-service/src/main/java/com/delivery/delivery_service/config/KafkaConfig.java"; then
  echo "delivery-service: isolated recovery must be able to disable Kafka listener startup." >&2
  exit 1
fi
delivery_command_listener="${ROOT_DIR}/delivery-service/src/main/java/com/delivery/delivery_service/listener/OrderEventListener.java"
delivery_inbound_receipt_service="${ROOT_DIR}/delivery-service/src/main/java/com/delivery/delivery_service/service/DeliveryInboundReceiptService.java"
delivery_saga_processor="${ROOT_DIR}/delivery-service/src/main/java/com/delivery/delivery_service/service/DeliverySagaCommandProcessor.java"
delivery_inbound_receipt_migration="${ROOT_DIR}/delivery-service/src/main/resources/db/migration/V14__delivery_inbound_command_receipts.sql"
if rg -q 'groupId\s*=\s*"delivery-service"' "${delivery_command_listener}" \
    || [[ "$(rg -c '\$\{app\.kafka\.topics\.' "${delivery_command_listener}")" -ne 5 ]]; then
  echo "delivery-service: command group/topics must be configurable for isolated recovery rehearsal." >&2
  exit 1
fi
if [[ ! -f "${delivery_inbound_receipt_migration}" ]] \
    || ! rg -Fq 'delivery_inbound_receipts' "${delivery_inbound_receipt_migration}" \
    || ! rg -Fq 'insertIfAbsentPostgres' "${delivery_inbound_receipt_service}" \
    || ! rg -Fq 'ON CONFLICT (event_id) DO NOTHING' \
      "${ROOT_DIR}/delivery-service/src/main/java/com/delivery/delivery_service/repository/DeliveryInboundReceiptRepository.java" \
    || ! rg -Fq 'DeliverySagaCommandProcessor' "${delivery_command_listener}" \
    || rg -q '@Transactional' "${delivery_command_listener}" \
    || ! rg -Fq '@Transactional' "${delivery_saga_processor}" \
    || [[ "$(rg -F -c 'receipts.claim(' "${delivery_saga_processor}")" -ne 1 ]] \
    || [[ "$(rg -F -c 'return receipts.claim(eventId' "${delivery_saga_processor}")" -ne 1 ]]; then
  echo "delivery-service: every Saga command must commit its durable receipt/mutation before listener ACK." >&2
  exit 1
fi
order_restaurant_listener="${ROOT_DIR}/order-service/src/main/java/com/delivery/order_service/listener/RestaurantEventListener.java"
order_saga_listener="${ROOT_DIR}/order-service/src/main/java/com/delivery/order_service/listener/SagaCommandListener.java"
order_saga_processor="${ROOT_DIR}/order-service/src/main/java/com/delivery/order_service/service/SagaOrderCommandProcessor.java"
order_saga_receipt_service="${ROOT_DIR}/order-service/src/main/java/com/delivery/order_service/service/SagaCommandReceiptService.java"
order_saga_receipt_migration="${ROOT_DIR}/order-service/src/main/resources/db/migration/V9__create_saga_command_receipts.sql"
order_properties="${ROOT_DIR}/order-service/src/main/resources/application.properties"
if [[ "$(rg -c '\$\{app\.kafka\.input-topics\.restaurant-' "${order_restaurant_listener}")" -ne 2 ]] \
    || ! rg -Fq '${app.kafka.input-topics.saga-update-order-status:saga.command.update-order-status}' \
      "${order_saga_listener}" \
    || rg -q 'groupId\s*=\s*"order-service"' "${order_saga_listener}" \
    || ! rg -Fq 'app.kafka.topics.order-created=${ORDER_CREATED_TOPIC:order.created}' \
      "${order_properties}" \
    || ! rg -Fq 'app.kafka.topics.order-cancelled=${ORDER_CANCELLED_TOPIC:order.cancelled}' \
      "${order_properties}"; then
  echo "order-service: active input group/topics and outbox destinations must support isolated recovery." >&2
  exit 1
fi
if [[ ! -f "${order_saga_receipt_migration}" ]] \
    || ! rg -Fq 'saga_command_receipts' "${order_saga_receipt_migration}" \
    || ! rg -Fq 'insertIfAbsentPostgres' "${order_saga_receipt_service}" \
    || ! rg -Fq 'ON CONFLICT (event_id) DO NOTHING' \
      "${ROOT_DIR}/order-service/src/main/java/com/delivery/order_service/repository/SagaCommandReceiptRepository.java" \
    || ! rg -Fq 'SagaOrderCommandProcessor' "${order_saga_listener}" \
    || ! rg -Fq '@Transactional' "${order_saga_processor}" \
    || ! rg -Fq 'SagaCommandReceiptService.UPDATE_ORDER_STATUS' "${order_saga_processor}" \
    || ! rg -Fq 'RawStringPreservingJsonMessageConverter' \
      "${ROOT_DIR}/order-service/src/main/java/com/delivery/order_service/config/KafkaConfig.java" \
    || ! rg -Fq '@Qualifier("retryKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate' \
      "${ROOT_DIR}/order-service/src/main/java/com/delivery/order_service/config/KafkaConfig.java"; then
  echo "order-service: Saga update-status must retain raw-payload ingress, transactional receipt, and raw DLT recovery." >&2
  exit 1
fi

cod_harness="${ROOT_DIR}/scripts/verify-mvp-cod-flow.sh"
failure_harness="${ROOT_DIR}/scripts/verify-mvp-failure-matrix.sh"
seed_harness="${ROOT_DIR}/scripts/seed.sh"
clean_harness="${ROOT_DIR}/scripts/verify-clean-compose-e2e.sh"
compose_harness="${ROOT_DIR}/scripts/verify-compose-config.sh"
runtime_startup_harness="${ROOT_DIR}/scripts/verify-runtime-startup.sh"
isolated_e2e_compose="${ROOT_DIR}/docker-compose.isolated-e2e.yml"
dockerfile="${ROOT_DIR}/Dockerfile"
docker_artifact_freshness_harness="${ROOT_DIR}/scripts/verify-docker-artifact-freshness.sh"
settlement_crash_harness="${ROOT_DIR}/scripts/verify-settlement-crash-window.sh"
settlement_crash_probe="${ROOT_DIR}/scripts/JdwpBreakpointProbe.java"
auth_user_outage_harness="${ROOT_DIR}/scripts/verify-auth-user-outage-retry.sh"
legacy_retry_drain_harness="${ROOT_DIR}/scripts/verify-kafka-legacy-retry-drain.sh"
legacy_retry_drain_test="${ROOT_DIR}/scripts/test-verify-kafka-legacy-retry-drain.sh"
prometheus_resilience_rule_verifier="${ROOT_DIR}/scripts/verify-prometheus-resilience-rules.sh"
compatibility_flow="${ROOT_DIR}/scripts/test-order-flow.sh"
if [[ ! -f "${settlement_crash_harness}" || ! -f "${settlement_crash_probe}" ]] \
    || ! rg -Fq "DeliveryCompletedEventListener\$1" "${settlement_crash_harness}" \
    || ! rg -Fq "BREAKPOINT_REACHED" "${settlement_crash_harness}" \
    || ! rg -Fq 'docker kill --signal=KILL' "${settlement_crash_harness}" \
    || ! rg -Fq 'database_invariants_hold' "${settlement_crash_harness}" \
    || ! rg -q 'lag.*==.*0' "${settlement_crash_harness}" \
    || ! rg -Fq 'BreakpointRequest' "${settlement_crash_probe}" \
    || [[ ! -f "${auth_user_outage_harness}" ]] \
    || ! rg -Fq 'auth_user_outage_auth_' "${auth_user_outage_harness}" \
    || ! rg -Fq 'auth_user_outage_user_' "${auth_user_outage_harness}" \
    || ! rg -Fq 'USER_SERVICE_URL=http://$USER_CONTAINER:8082' "${auth_user_outage_harness}" \
    || ! rg -Fq 'user_status_sync_pending = true AND user_status_sync_attempts > 0' "${auth_user_outage_harness}" \
    || ! rg -Fq 'user_status_sync_pending = false AND user_status_sync_attempts = 0' "${auth_user_outage_harness}" \
    || [[ ! -f "${legacy_retry_drain_harness}" ]] \
    || [[ ! -f "${legacy_retry_drain_test}" ]] \
    || [[ ! -f "${prometheus_resilience_rule_verifier}" ]] \
    || ! rg -Fq 'PROVISION_LEGACY_SHARED_RETRY_TOPICS=false' "${legacy_retry_drain_harness}" \
    || ! rg -Fq 'KAFKA_RETRY_ATTEMPTS' "${legacy_retry_drain_harness}" \
    || ! rg -Fq 'KAFKA_RETRY_MULTIPLIER' "${legacy_retry_drain_harness}" \
    || ! rg -Fq 'ConsumerFactory group-id' "${legacy_retry_drain_harness}" \
    || ! rg -Fq 'Legacy retry topic end offsets advanced during the quiet window' "${legacy_retry_drain_harness}" \
    || ! rg -Fq 'still has an active consumer assigned' "${legacy_retry_drain_harness}" \
    || ! rg -Fq 'configured base consumer groups' "${legacy_retry_drain_test}" \
    || ! rg -Fq 'derive retry topics from the supplied retry policy' "${legacy_retry_drain_test}" \
    || ! rg -Fq 'Kafka legacy retry drain verifier contract tests passed.' "${legacy_retry_drain_test}" \
    || ! rg -Fq 'DeliveryKafkaDltIncreasing' "${prometheus_resilience_rule_verifier}" \
    || ! rg -Fq 'check rules /etc/prometheus/rules/resilience.yml' "${prometheus_resilience_rule_verifier}" \
    || ! rg -Fq 'test rules /etc/prometheus/tests/resilience-rules.test.yml' "${prometheus_resilience_rule_verifier}" \
    || rg -q 'compose (stop|rm).*user-service|docker rm -f user-service' "${auth_user_outage_harness}" \
    || rg -Fq '/api/deliveries/order/$order_id' "${cod_harness}" \
    || ! rg -Fq '/api/notifications/unread' "${cod_harness}" \
    || ! rg -Fq '/api/deliveries/offers/current' "${cod_harness}" \
    || ! rg -Fq 'TrackingPublisherProbe.java' "${cod_harness}" \
    || ! rg -Fq '/api/tracking/shipper-locations/offline' "${cod_harness}" \
    || ! rg -Fq 'order_status' "${cod_harness}" \
    || ! rg -Fq 'verify-mvp-failure-matrix.sh' "${clean_harness}" \
    || ! rg -Fq '/api/restaurants/orders/$rejected_order_id/reject' "${failure_harness}" \
    || ! rg -Fq 'SHIPPER_NOT_FOUND' "${failure_harness}" \
    || ! rg -Fq '/api/deliveries/cancel-assignment' "${failure_harness}" \
    || ! rg -Fq 'status=DELIVERED' "${failure_harness}" \
    || ! rg -Fq 'recoveryEndpoint' "${failure_harness}" \
    || ! rg -Fq 'COMPOSE_FILE:-' "${seed_harness}" \
    || ! rg -Fq 'COMPOSE_FILE:-' "${cod_harness}" \
    || ! rg -Fq 'COMPOSE_FILE:-' "${failure_harness}" \
    || rg -Fq 'docker compose exec' "${seed_harness}" \
    || rg -Fq 'docker compose exec' "${cod_harness}" \
    || rg -Fq 'docker compose exec' "${failure_harness}" \
    || rg -Fq 'docker compose run' "${seed_harness}" \
    || rg -Fq 'docker compose run' "${failure_harness}" \
    || [[ ! -f "${isolated_e2e_compose}" ]] \
    || ! rg -Fq 'docker-compose.isolated-e2e.yml' "${clean_harness}" \
    || ! rg -Fq 'RUNTIME_ISOLATED=true' "${clean_harness}" \
    || ! rg -Fq 'CLEAN_E2E_CONFIG_ONLY' "${clean_harness}" \
    || ! rg -Fq 'COMPOSE_FILE="$CLEAN_COMPOSE_FILE"' "${clean_harness}" \
    || rg -Fq 'ALLOW_CANONICAL_DOWNTIME' "${clean_harness}" \
    || rg -Fq 'canonical_compose' "${clean_harness}" \
    || ! rg -Fq 'mvn -q -DskipTests package' "${clean_harness}" \
    || ! rg -Fq 'CLEAN_BASE' "${clean_harness}" \
    || ! rg -Fq 'clean_compose port api-gateway 8079' "${clean_harness}" \
    || ! rg -Fq 'MATCHING_INITIAL_MAX_RETRY_ATTEMPTS="$CLEAN_MATCHING_MAX_RETRY_ATTEMPTS"' "${clean_harness}" \
    || ! rg -Fq 'rendered_isolated_e2e_config' "${compose_harness}" \
    || ! rg -Fq 'expected_kafka_volume_name="${KAFKA_VOLUME_NAME:-backend_delivery_kafka_data}"' "${compose_harness}" \
    || ! rg -Fq 'detected_postgres_volume' "${runtime_startup_harness}" \
    || ! rg -Fq 'detected_postgres_host_port' "${runtime_startup_harness}" \
    || ! rg -Fq 'detected_kafka_volume' "${runtime_startup_harness}" \
    || ! rg -Fq 'export POSTGRES_VOLUME_NAME=' "${runtime_startup_harness}" \
    || ! rg -Fq 'EUREKA_REGISTRATION_TIMEOUT_SECONDS' "${runtime_startup_harness}" \
    || ! rg -Fq 'ensure_eureka_registration' "${runtime_startup_harness}" \
    || ! rg -Fq 'wait_for_eureka_registration' "${runtime_startup_harness}" \
    || ! rg -Fq 'RUNTIME_REBUILD_IMAGES' "${runtime_startup_harness}" \
    || ! rg -Fq 'compose_up --no-deps auth-service' "${runtime_startup_harness}" \
    || ! rg -Fq 'compose_up --no-deps "${RESOURCE_APP_SERVICES[@]}"' "${runtime_startup_harness}" \
    || ! rg -Fq 'compose_up --no-deps api-gateway' "${runtime_startup_harness}" \
    || ! rg -Fq 'RUNTIME_REBUILD_IMAGES=true' "${clean_harness}" \
    || ! rg -Fq 'COPY ${SERVICE_PATH}/src service/src' "${dockerfile}" \
    || ! rg -Fq 'find service/src service/pom.xml reactor-pom.xml -type f -newer "$artifact"' "${dockerfile}" \
    || ! rg -Fq 'run Maven package first' "${dockerfile}" \
    || [[ ! -f "${docker_artifact_freshness_harness}" ]] \
    || ! rg -Fq 'Docker accepted a stale packaged JAR.' "${docker_artifact_freshness_harness}" \
    || ! rg -Fq 'is stale (newer input:' "${docker_artifact_freshness_harness}" \
    || ! rg -Fq 'clean_compose down -v --remove-orphans' "${clean_harness}" \
    || ! rg -Fq 'verify-mvp-cod-flow.sh' "${compatibility_flow}" \
    || rg -q '/api/(deliveries/order|settlement/balances)' "${compatibility_flow}"; then
  echo "Gate B8 harness must preserve settlement crash recovery, durable offer recovery/raw WebSocket and isolated-volume safety." >&2
  exit 1
fi
user_service_contract="${ROOT_DIR}/user-service/src/main/java/com/delivery/user_service/service/UserService.java"
user_service_implementation="${ROOT_DIR}/user-service/src/main/java/com/delivery/user_service/service/impl/UserServiceImpl.java"
if [[ -e "${ROOT_DIR}/user-service/src/main/java/com/delivery/user_service/controller/LegacyUserDeleteController.java" ]] \
    || rg -q 'deleteUser|userRepository\.delete(ById)?' \
      "${user_service_contract}" \
      "${user_service_implementation}"; then
  echo "user-service: direct profile hard-delete must not be restored; account lifecycle is Auth-owned soft deactivation." >&2
  exit 1
fi

"${ROOT_DIR}/scripts/verify-http-api-inventory.sh"
bash "${ROOT_DIR}/scripts/verify-actuator-config.sh"
bash "${ROOT_DIR}/scripts/verify-prometheus-resilience-rules.sh"

echo "Build baseline is valid: Java and Maven use JDK 17, Spring Boot ${BOOT_VERSION}, Spring Cloud ${CLOUD_VERSION}."
