package com.delivery.api_gateway.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsConfigTest {

    @Test
    void defaultLocalOriginsIncludeVitePreviewPortsUsedByRuntimeSmoke() {
        CorsConfig config = new CorsConfig(Arrays.asList(CorsConfig.DEFAULT_ALLOWED_ORIGINS.split(",")));

        assertThat(config.allowedOrigins())
                .contains(
                        "http://localhost:5173",
                        "http://127.0.0.1:5173",
                        "http://localhost:4173",
                        "http://127.0.0.1:4173");
    }

    @Test
    void configuredOriginsAreTrimmedAndCannotBeEmpty() {
        CorsConfig config = new CorsConfig(List.of(" http://localhost:4173 ", "", " http://localhost:3000 "));

        assertThat(config.allowedOrigins())
                .containsExactly("http://localhost:4173", "http://localhost:3000");

        assertThatThrownBy(() -> new CorsConfig(List.of(" ", "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("At least one CORS origin");
    }
}
