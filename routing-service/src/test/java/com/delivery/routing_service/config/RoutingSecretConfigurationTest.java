package com.delivery.routing_service.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.routing_service.RoutingServiceApplication;
import com.delivery.routing_service.api.Coordinate;
import com.delivery.routing_service.api.MatrixRequest;
import com.delivery.routing_service.controller.RoutingController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;

class RoutingSecretConfigurationTest {

    @TempDir
    Path configTree;

    @Test
    void configTreeSecretSatisfiesTheRuntimeGuardAndProtectsRoutingEndpoints() throws IOException {
        String expectedSecret = "routing-configtree-secret";
        Files.writeString(configTree.resolve("internal-secret"), expectedSecret + "\n", StandardCharsets.UTF_8);

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(RoutingServiceApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.config.import=optional:configtree:" + configTree + "/",
                        "--spring.cloud.config.enabled=false",
                        "--eureka.client.enabled=false",
                        "--MAPBOX_SERVER_TOKEN=",
                        "--platform.secrets.internal-secret-required=true")) {
            assertThat(context.getBean(RoutingProperties.class).getInternalSecret()).isEqualTo(expectedSecret);

            MatrixRequest request = new MatrixRequest(
                    "driving",
                    new Coordinate(10.76, 106.66),
                    List.of(new MatrixRequest.Destination("destination", new Coordinate(10.78, 106.68))),
                    null);
            RoutingController controller = context.getBean(RoutingController.class);
            assertThat(controller.matrix(expectedSecret, request).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(controller.matrix("different-secret", request).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
