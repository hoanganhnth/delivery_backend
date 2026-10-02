package com.delivery.shipper_service;

import com.delivery.shipper.application.api.ShipperCommands;
import com.delivery.shipper.application.api.ShipperUseCases;
import com.delivery.shipper.domain.identity.ShipperRole;
import com.delivery.shipper.infrastructure.repository.ShipperRepository;
import com.delivery.shipper.infrastructure.repository.ShipperIdentityOutboxEventRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ShipperServiceApplication.class, properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "spring.kafka.listener.auto-startup=false", "spring.jpa.show-sql=false"
})
class ShipperPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired ShipperUseCases.CreateProfile create;
    @Autowired ShipperRepository profiles;
    @MockitoSpyBean ShipperIdentityOutboxEventRepository events;

    @Test void createsProfileAndItsIdentityEventInOneCommittedTransaction() {
        var profile = create.execute(command(901, 1901)).profile();
        var event = events.findAll().stream().filter(row -> row.getAggregateId().equals(profile.id())).findFirst();
        assertThat(event).isPresent();
        assertThat(event.orElseThrow().getPayload()).contains("\"principalId\":901", "\"shipperId\":" + profile.id());
        assertThat(profiles.findByPrincipalId(901L)).isPresent();
    }

    @Test void failedOutboxInsertionRollsBackNewProfile() {
        doThrow(new IllegalStateException("fixture outbox unavailable")).when(events).insertIfAbsent(any(), anyString(), anyLong(), anyString(), anyString(), anyString());
        assertThatThrownBy(() -> create.execute(command(902, 1902))).isInstanceOf(IllegalStateException.class);
        assertThat(profiles.findByPrincipalId(902L)).isEmpty();
    }

    @Autowired ShipperUseCases.UpdateProfile update;

    @Test void unchangedOwnedDocumentsCanBeSavedWhileAnotherShippersDocumentsStayUnique() {
        var first = create.execute(command(903, 1903)).profile();
        var second = create.execute(command(904, 1904)).profile();
        var owner = new ShipperCommands.Actor(903, 1903L, ShipperRole.SHIPPER);
        var sameDocuments = new ShipperCommands.UpdateProfile(owner, first.id(), "Updated Fixture", null,
                first.licenseNumber(), first.idCard(), null, null, null, null, null, null);
        assertThat(update.execute(sameDocuments).fullName()).isEqualTo("Updated Fixture");
        assertThatThrownBy(() -> update.execute(new ShipperCommands.UpdateProfile(owner, first.id(), "Invalid Update", null,
                second.licenseNumber(), null, null, null, null, null, null, null))).isInstanceOf(IllegalArgumentException.class);
        assertThat(profiles.findByPrincipalId(903L).orElseThrow().getFullName()).isEqualTo("Updated Fixture");
    }

    @Autowired org.springframework.test.web.servlet.MockMvc http;

    @Test void profileHttpPreservesDocumentImagesAndAuditTimestamps() throws Exception {
        var actor = new com.delivery.auth.resourceserver.security.AuthenticatedActor(905L, 1905L,
                "shipper@example.test", java.util.Set.of("SHIPPER"));
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("fixture-only")
                .header("alg", "RS256").subject("fixture").build();
        var authentication = new com.delivery.auth.resourceserver.security.AuthenticatedActorAuthenticationToken(jwt, actor,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SHIPPER")));
        var browser = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(authentication);
        var created = http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/shippers")
                .with(browser).contentType("application/json").content("""
                {"fullName":"Photo Fixture","vehicleType":"BIKE","licenseNumber":"PHOTO-LICENSE",
                 "idCard":"PHOTO-CARD","phone":"0900000000","driverImage":"fixture-driver",
                 "idCardFrontImage":"fixture-front","idCardBackImage":"fixture-back","licenseImage":"fixture-license"}
                """))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.driverImage").value("fixture-driver"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.idCardFrontImage").value("fixture-front"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.idCardBackImage").value("fixture-back"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.licenseImage").value("fixture-license"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.createdAt").exists())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.updatedAt").exists()).andReturn();
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/shippers")
                .with(browser).contentType("application/json").content("{\"driverImage\":\"updated-driver\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.driverImage").value("updated-driver"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.idCardFrontImage").value("fixture-front"));
        assertThat(profiles.findByPrincipalId(905L).orElseThrow().getDriverImage()).isEqualTo("updated-driver");
    }

    private ShipperCommands.CreateProfile command(long principal, long user) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return new ShipperCommands.CreateProfile(new ShipperCommands.Actor(principal, user, ShipperRole.SHIPPER),
                "Fixture Shipper", "BIKE", "LICENSE-" + suffix, "CARD-" + suffix, "0900000000", "TEST-1", null, null, null, null);
    }
}
