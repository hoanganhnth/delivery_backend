package com.delivery.auth_service;

import com.delivery.auth.application.api.AccountLifecycleUseCase;
import com.delivery.auth.application.api.RegisterCommand;
import com.delivery.auth.application.api.RegistrationUseCase;
import com.delivery.auth_service.repository.*;
import com.delivery.auth_service.service.IdentityProfileEventListener;
import com.delivery.identity.contracts.IdentityLifecycleStatus;
import com.delivery.identity.contracts.IdentityProfileCreated;
import com.delivery.identity.contracts.IdentityStatusChanged;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes=AuthServiceApplication.class, properties={
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "spring.kafka.listener.auto-startup=false", "app.identity.events.enabled=true",
        "app.identity.outbox.relay-enabled=false", "app.identity.public-registration-enabled=true",
        "app.user-status-sync.poll-delay-ms=3600000"
})
class IdentityProfileEventPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void fixture(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",POSTGRES::getUsername);
        registry.add("spring.datasource.password",POSTGRES::getPassword);
        TestJwtKeyProperties.register(registry);
    }
    @Autowired RegistrationUseCase registration;
    @Autowired AccountLifecycleUseCase lifecycle;
    @Autowired IdentityProfileEventListener listener;
    @Autowired AuthAccountRepository accounts;
    @Autowired IdentityOutboxEventRepository outbox;
    @MockitoSpyBean IdentityInboxReceiptRepository receipts;
    @Autowired ObjectMapper mapper;
    @MockitoBean com.delivery.auth_service.service.SecurityEmailSender email;

    @Test void listenerLinksExactlyOnceAndRejectsConflictingEventReuse() throws Exception {
        Long id=registration.register(new RegisterCommand("pg-profile-inbox@example.test","Password1!","USER")).account().id();
        var event=new IdentityProfileCreated(UUID.randomUUID(),IdentityProfileCreated.TYPE,1,Instant.now(),UUID.randomUUID(),
                null,id,"USER_PROFILE",9601L,1L);
        String raw=mapper.writeValueAsString(event);
        listener.profileCreated(raw);
        var linked=accounts.findById(id).orElseThrow();
        assertThat(linked.getUserId()).isEqualTo(9601L);
        assertThat(linked.getLifecycleStatus()).isEqualTo(IdentityLifecycleStatus.PENDING_EMAIL_VERIFICATION);
        assertThat(linked.getLifecycleVersion()).isEqualTo(1L);
        assertThat(receipts.findById(event.eventId())).isPresent();
        var output=outbox.findAll().stream().filter(row->row.getAggregateId().equals(id)).toList();
        assertThat(output).hasSize(1);
        var status=mapper.readValue(output.get(0).getPayload(),IdentityStatusChanged.class);
        assertThat(status.status()).isEqualTo(IdentityLifecycleStatus.PENDING_EMAIL_VERIFICATION);
        assertThat(status.lifecycleVersion()).isEqualTo(1L);
        assertThat(status.reasonCode()).isEqualTo("PROFILE_COMPLETED");
        listener.profileCreated(raw);
        assertThat(outbox.findAll().stream().filter(row->row.getAggregateId().equals(id))).hasSize(1);
        var conflict=new IdentityProfileCreated(event.eventId(),IdentityProfileCreated.TYPE,1,Instant.now(),event.correlationId(),
                null,id,"USER_PROFILE",9602L,1L);
        assertThatThrownBy(()->listener.profileCreated(mapper.writeValueAsString(conflict)))
                .hasMessage("Conflicting identity event reuse");
        assertThat(accounts.findById(id).orElseThrow().getUserId()).isEqualTo(9601L);
    }
    @Test void blockedIdentityReplaysStatusWhenProfileArrivesAfterEarlierBlockedEvent() throws Exception {
        Long id=registration.register(new RegisterCommand("pg-profile-blocked@example.test","Password1!","USER")).account().id();
        lifecycle.block(id,99L,"fraud review");
        assertThat(outbox.findAll().stream().filter(row->row.getAggregateId().equals(id))).hasSize(1);
        listener.profileCreated(wire(id,9603L));
        assertThat(accounts.findById(id).orElseThrow().getIsActive()).isFalse();
        assertThat(accounts.findById(id).orElseThrow().getLifecycleStatus()).isEqualTo(IdentityLifecycleStatus.BLOCKED);
        var messages=outbox.findAll().stream().filter(row->row.getAggregateId().equals(id)).map(row->row.getPayload()).toList();
        assertThat(messages).hasSize(2);
        assertThat(mapper.readValue(messages.get(1),IdentityStatusChanged.class).reasonCode()).isEqualTo("PROFILE_COMPLETED");
    }
    @Test void receiptWriteFailureRollsBackProfileLinkAndStatusOutbox() throws Exception {
        Long id=registration.register(new RegisterCommand("pg-profile-rollback@example.test","Password1!","USER")).account().id();
        doThrow(new IllegalStateException("fixture inbox unavailable")).when(receipts).save(any());
        assertThatThrownBy(()->listener.profileCreated(wire(id,9604L))).hasMessage("fixture inbox unavailable");
        var account=accounts.findById(id).orElseThrow();
        assertThat(account.getUserId()).isNull();
        assertThat(account.getLifecycleStatus()).isEqualTo(IdentityLifecycleStatus.PENDING_PROFILE);
        assertThat(account.getLifecycleVersion()).isZero();
        assertThat(outbox.findAll().stream().filter(row->row.getAggregateId().equals(id))).isEmpty();
    }
    private String wire(Long id,Long profile) throws Exception {
        return mapper.writeValueAsString(new IdentityProfileCreated(UUID.randomUUID(),IdentityProfileCreated.TYPE,1,
                Instant.now(),UUID.randomUUID(),null,id,"USER_PROFILE",profile,1L));
    }
}
