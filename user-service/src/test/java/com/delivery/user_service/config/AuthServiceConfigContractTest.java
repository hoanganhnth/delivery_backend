package com.delivery.user_service.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceConfigContractTest {

    @Test
    void composeDefaultUsesAuthApplicationPort() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/application.properties")) {
            assertThat(input).isNotNull();
            properties.load(input);
        }

        assertThat(properties.getProperty("auth-service.url"))
                .isEqualTo("${AUTH_SERVICE_URL:http://auth-service:8081}");
    }
}
