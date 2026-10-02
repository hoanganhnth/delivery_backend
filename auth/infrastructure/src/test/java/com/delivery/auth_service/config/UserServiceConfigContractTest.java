package com.delivery.auth_service.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class UserServiceConfigContractTest {

    @Test
    void defaultUserServiceUrlUsesComposeApplicationPort() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/application.properties")) {
            assertThat(input).isNotNull();
            properties.load(input);
        }

        assertThat(properties.getProperty("user-service.url"))
                .isEqualTo("${USER_SERVICE_URL:http://user-service:8082}");
    }
}
