package com.delivery.auth_service.config;

import org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * V8 creates the canonical-email unique index with PostgreSQL's
 * {@code CONCURRENTLY} syntax. Flyway must therefore use its session-scoped
 * advisory lock; a transactional advisory lock leaves an old transaction that
 * makes PostgreSQL wait on the migration itself.
 */
@Configuration(proxyBeanMethods = false)
public class AuthFlywayConfiguration {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    FlywayConfigurationCustomizer canonicalEmailConcurrentIndexFlywayCustomizer() {
        return configuration -> configuration.getPluginRegister()
                .getPlugin(PostgreSQLConfigurationExtension.class)
                .setTransactionalLock(false);
    }
}
