FROM amazoncorretto:17-alpine AS artifact-check
ARG SERVICE_PATH

WORKDIR /build
COPY pom.xml reactor-pom.xml
COPY ${SERVICE_PATH}/pom.xml service/pom.xml
COPY ${SERVICE_PATH}/src service/src
COPY ${SERVICE_PATH}/target/*.jar service/target/

# Compose images intentionally consume Maven artifacts built on the host. Refuse
# to package an older JAR when source or build metadata changed after Maven ran.
RUN set -eu; \
    set -- service/target/*.jar; \
    if [ "$#" -ne 1 ]; then \
      echo "Expected exactly one packaged JAR for ${SERVICE_PATH}; run Maven package first." >&2; \
      exit 1; \
    fi; \
    artifact="$1"; \
    stale_input="$(find service/src service/pom.xml reactor-pom.xml -type f -newer "$artifact" -print -quit)"; \
    if [ -n "$stale_input" ]; then \
      echo "Packaged JAR for ${SERVICE_PATH} is stale (newer input: ${stale_input}); run Maven package first." >&2; \
      exit 1; \
    fi

FROM amazoncorretto:17-alpine
WORKDIR /app
RUN apk add --no-cache wget
COPY --from=artifact-check /build/service/target/*.jar app.jar
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
  CMD wget -q -T 3 -O /dev/null "http://localhost:${MANAGEMENT_SERVER_PORT:-9090}/actuator/health/readiness" || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
