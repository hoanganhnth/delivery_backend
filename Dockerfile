FROM amazoncorretto:17-alpine
WORKDIR /app
ARG SERVICE_PATH
COPY ${SERVICE_PATH}/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
