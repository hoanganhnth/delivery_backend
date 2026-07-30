package com.delivery.order_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@ActiveProfiles("test")
class PrometheusEndpointIntegrationTest {

    @LocalManagementPort
    private int managementPort;

    @Value("${management.endpoints.web.base-path:/actuator}")
    private String basePath;

    @Test
    void exposesPrometheusFormattedMetricsOnTheManagementServer() {
        ResponseEntity<String> response = new TestRestTemplate().getForEntity(
                "http://localhost:" + managementPort + basePath + "/prometheus", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType().toString())
                .contains("text/plain");
        assertThat(response.getBody()).contains("jvm_memory_used_bytes");
    }
}
