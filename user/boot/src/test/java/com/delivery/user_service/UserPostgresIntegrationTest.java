package com.delivery.user_service;

import com.delivery.user.application.api.*;
import com.delivery.user_service.repository.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = UserServiceApplication.class, properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "spring.kafka.listener.auto-startup=false", "app.identity.events.enabled=false",
        "app.identity.outbox.relay-enabled=false"
})
class UserPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired UserProfileUseCase profiles;
    @Autowired UserAddressUseCase addresses;
    @Autowired UserRepository users;
    @Autowired org.springframework.jdbc.core.JdbcTemplate sql;
    @MockitoSpyBean IdentityOutboxEventRepository events;
    @MockitoSpyBean UserAddressRepository addressRows;

    @Test void profileAndOutboxCommitTogetherAndReplayDoesNotDuplicateThem() {
        var first = profiles.create(profile(901));
        assertThat(profiles.create(profile(901)).id()).isEqualTo(first.id());
        assertThat(sql.queryForObject("select count(*) from identity_outbox_events where aggregate_id = ?", Long.class, 901L)).isEqualTo(1L);
        assertThat(users.findByPrincipalId(901L)).isPresent();
    }

    @Test void outboxFailureRollsBackProfileInsertion() {
        doThrow(new IllegalStateException("fixture outbox failure")).when(events)
                .insertProfileCreatedIfAbsent(any(), anyString(), anyLong(), anyString(), anyString(), anyString());
        assertThatThrownBy(() -> profiles.create(profile(902))).isInstanceOf(IllegalStateException.class);
        assertThat(users.findByPrincipalId(902L)).isEmpty();
    }

    @Test void concurrentProvisioningConvergesOnOneProfileAndOutbox() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<UserProfileResult> provision = () -> { start.await(); return profiles.create(profile(903)); };
            Future<UserProfileResult> first = pool.submit(provision);
            Future<UserProfileResult> second = pool.submit(provision);
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS).id()).isEqualTo(second.get(15, TimeUnit.SECONDS).id());
            assertThat(sql.queryForObject("select count(*) from users where principal_id = 903", Long.class)).isEqualTo(1L);
            assertThat(sql.queryForObject("select count(*) from identity_outbox_events where aggregate_id = 903", Long.class)).isEqualTo(1L);
        } finally { pool.shutdownNow(); }
    }

    @Test void defaultTransitionsPreserveNullUpdateAndPromoteLatestRemainingAddress() {
        Long user = profiles.create(profile(904)).id();
        var first = addresses.create(address(user, true, "first"));
        var second = addresses.create(address(user, true, "second"));
        assertThat(addresses.byId(first.id()).isDefault()).isFalse();
        assertThat(addresses.byId(second.id()).isDefault()).isTrue();
        addresses.update(new UpdateUserAddressCommand(second.id(), "updated", null, null, "line", null,
                null, "city", null, null, null, null));
        assertThat(addresses.byId(second.id()).isDefault()).isTrue();
        addresses.delete(second.id());
        assertThat(addresses.byId(first.id()).isDefault()).isTrue();
        addresses.delete(first.id());
        assertThat(addresses.byUserId(user)).isEmpty();
    }

    @Test void failedDefaultInsertRollsBackClearingPreviousDefault() {
        Long user = profiles.create(profile(905)).id();
        var original = addresses.create(address(user, true, "original"));
        doThrow(new IllegalStateException("fixture address write failure")).when(addressRows).save(any());
        assertThatThrownBy(() -> addresses.create(address(user, true, "failure"))).isInstanceOf(IllegalStateException.class);
        assertThat(addresses.byId(original.id()).isDefault()).isTrue();
        assertThat(addresses.byUserId(user)).hasSize(1);
    }

    @Test void concurrentDefaultSelectionLeavesExactlyOneDefault() throws Exception {
        Long user = profiles.create(profile(906)).id();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<UserAddressResult> first = pool.submit(() -> { start.await(); return addresses.create(address(user, true, "one")); });
            Future<UserAddressResult> second = pool.submit(() -> { start.await(); return addresses.create(address(user, true, "two")); });
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
            assertThat(addresses.byUserId(user)).hasSize(2);
            assertThat(addresses.byUserId(user).stream().filter(row -> Boolean.TRUE.equals(row.isDefault())).count()).isEqualTo(1L);
        } finally { pool.shutdownNow(); }
    }

    private CreateUserCommand profile(long principal) {
        return new CreateUserCommand(principal, principal, "fixture-" + principal + "@example.test", "USER",
                "Fixture", null, null, null, null);
    }
    private CreateUserAddressCommand address(Long user, Boolean isDefault, String label) {
        return new CreateUserAddressCommand(user, label, "Fixture", "0900000000", "line", null,
                null, "city", null, null, null, isDefault);
    }
}
