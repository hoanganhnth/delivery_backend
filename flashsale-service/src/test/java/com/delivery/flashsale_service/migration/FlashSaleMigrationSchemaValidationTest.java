package com.delivery.flashsale_service.migration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:flash_sale_flyway_validation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.kafka.listener.auto-startup=false",
        "app.flashsale.checkout-enabled=false",
        "app.flashsale.merchant-registration-enabled=false",
        "restaurant.service.url=http://restaurant-service"
})
class FlashSaleMigrationSchemaValidationTest {

    @Test
    void flywaySchemaMatchesFlashSaleJpaEntitiesWithoutHibernateMutation() {
    }
}
