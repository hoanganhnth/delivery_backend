package com.delivery.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.core.env.Environment;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "management.endpoints.web.exposure.include=health",
                "management.endpoint.health.probes.enabled=true",
                "management.health.livenessstate.enabled=true",
                "management.health.readinessstate.enabled=true",
                "management.endpoint.health.group.liveness.include=livenessState",
                "management.endpoint.health.group.readiness.include=readinessState",
                "management.health.redis.enabled=false",
                "management.health.eureka.enabled=false",
                "management.endpoint.health.show-details=never",
                "management.endpoint.health.show-components=never"
        })
class ActuatorProbeEndpointTest {

    @LocalManagementPort
    private int managementPort;

    @LocalServerPort
    private int applicationPort;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private Environment environment;

    @Test
    void exposesSanitizedHealthAndProbeEndpointsOnlyOnManagementPort() {
        org.assertj.core.api.Assertions.assertThat(environment.getProperty(
                "management.endpoint.health.probes.enabled")).isEqualTo("true");
        WebTestClient managementClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + managementPort)
                .build();

        managementClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.components").doesNotExist();
        managementClient.get().uri("/actuator/health/liveness")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
        managementClient.get().uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");

        webTestClient.get().uri("http://localhost:" + applicationPort + "/actuator/health")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }
}
