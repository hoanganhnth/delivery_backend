package com.delivery.auth_service.migration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.delivery.auth_service.TestJwtKeyProperties;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_flyway_validation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.kafka.listener.auto-startup=false"
})
class AuthMigrationSchemaValidationTest {

    @DynamicPropertySource
    static void jwtKeyProperties(DynamicPropertyRegistry registry) {
        TestJwtKeyProperties.register(registry);
    }

    @Test
    void flywaySchemaMatchesAuthJpaEntitiesWithoutHibernateMutation() {
    }
}
