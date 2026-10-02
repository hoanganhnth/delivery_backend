package com.delivery.auth_service;

import com.delivery.auth.application.api.*;
import com.delivery.auth_service.repository.*;
import com.delivery.auth_service.service.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
@SpringBootTest(classes = AuthServiceApplication.class, properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "spring.kafka.listener.auto-startup=false", "app.identity.events.enabled=false",
        "app.identity.outbox.relay-enabled=false", "app.identity.public-registration-enabled=true",
        "app.identity.registration.canary-percentage=100",
        "app.user-status-sync.poll-delay-ms=3600000"
})
class AuthPostgresWorkflowIntegrationTest {
    private static final java.util.concurrent.atomic.AtomicReference<byte[]> JWKS =
            new java.util.concurrent.atomic.AtomicReference<>();
    private static final com.sun.net.httpserver.HttpServer JWKS_SERVER = startJwksServer();
    private static com.sun.net.httpserver.HttpServer startJwksServer() {
        try {
            var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/jwks", exchange -> {
                byte[] body = JWKS.get();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            server.start();
            return server;
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    @org.junit.jupiter.api.BeforeEach void publishFixtureJwks() throws Exception {
        JWKS.set(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(tokens.getJwks()));
    }
    @org.junit.jupiter.api.AfterAll static void stopJwksServer() { JWKS_SERVER.stop(0); }

    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void fixture(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        TestJwtKeyProperties.register(registry);
        registry.add("app.auth.jwks-uri", () -> "http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort() + "/jwks");
    }
    @Autowired AccountLifecycleUseCase lifecycle;
    @Autowired LifecycleTransactionPort lifecycleTransactions;
    @Autowired LifecycleTelemetryPort lifecycleTelemetry;
    @MockitoSpyBean IdentityOutboxEventRepository outboxEvents;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    @MockitoBean UserStatusProjectionPort userStatusProjection;
    @Autowired SimulationBindingUseCase simulationBindings;
    @Autowired AuthAccountLockPort lockedAccounts;
    @Autowired RegistrationUseCase registration;
    @Autowired OperatorProvisioningUseCase operators;
    @Autowired SocialLoginUseCase social;
    @MockitoBean SocialIdentityPort socialIdentities;
    @Autowired AuthAccountPort accountFlow;
    @Autowired AuthTransactionPort transactions;
    @Autowired PasswordCredentialPort credentials;
    @Autowired UserProfileProvisioningUseCase profileFlow;
    @Autowired SessionPort sessionFlow;
    @Autowired SessionTokenPort sessionTokens;
    @Autowired RefreshCredentialPort refreshFlow;
    @MockitoBean UserProfileProvisioningPort profiles;
    @Autowired LoginUseCase login;
    @Autowired RefreshTokenUseCase refresh;
    @Autowired LogoutUseCase logout;
    @Autowired DeviceSessionUseCase devices;
    @MockitoSpyBean AuthSessionRepository sessions;
    @Autowired org.springframework.jdbc.core.JdbcTemplate sql;
    @MockitoSpyBean RefreshTokenRecordRepository refreshRecords;
    @Autowired AuthAccountRepository accounts;
    @Autowired RegistrationRecoveryUseCase statuses;
    @Autowired TokenService tokens;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired org.springframework.test.web.servlet.MockMvc http;
    @MockitoSpyBean IdentityRegistrationRepository handles;
    @MockitoBean SecurityEmailSender email;

    @Test void firebaseDisabledKeepsUnavailableAndIdentityAuthorizationHttpContracts() throws Exception {
        for (String role : new String[]{"USER", "ADMIN"}) {
            String access = tokens.generateToken(84L, 42L, "chat@example.test", role);
            http.perform(post("/api/auth/firebase/chat-token").header("Authorization", "Bearer " + access))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message").value("Support chat is temporarily unavailable"));
        }
        String shipper = tokens.generateToken(84L, 42L, "chat@example.test", "SHIPPER");
        http.perform(post("/api/auth/firebase/chat-token").header("Authorization", "Bearer " + shipper))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Support chat is not available for this identity"));
    }

    @Test void blockingCommitsIdentityBeforeProjectionAndAtomicallyRevokesSessionsAndRefreshCredentials() {
        Long id = activeAccount("pg-block@example.test", 9501L);
        login.login(loginCommand("pg-block@example.test", "phone"));
        doAnswer(call -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(sql.queryForObject("select is_active from auth_account where id=?", Boolean.class, id)).isFalse();
            assertThat(sql.queryForObject("select count(*) from auth_refresh_token t join auth_session s on s.id=t.session_id where s.auth_id=? and t.state='CURRENT'",Long.class,id)).isZero();
            return null;
        }).when(userStatusProjection).synchronize(9501L, 99L, "fraud review", true);
        lifecycle.block(id, 99L, "fraud review");
        var persisted = accounts.findById(id).orElseThrow();
        assertThat(persisted.getLifecycleStatus()).isEqualTo(com.delivery.identity.contracts.IdentityLifecycleStatus.BLOCKED);
        assertThat(persisted.getLifecycleVersion()).isEqualTo(1L);
        assertThat(persisted.getUserStatusSyncPending()).isFalse();
        assertThat(persisted.getUserStatusSyncAdminId()).isNull();
        assertThat(devices.activeSessions("pg-block@example.test")).isEmpty();
        verify(userStatusProjection).synchronize(9501L, 99L, "fraud review", true);
        assertThatThrownBy(() -> login.login(loginCommand("pg-block@example.test", "phone")))
                .isInstanceOf(com.delivery.auth.domain.policy.CredentialsRejected.class);
    }

    @Test void revocationFailureRollsBackTheEntireBlockDecision() {
        Long id = activeAccount("pg-block-rollback@example.test", 9502L);
        login.login(loginCommand("pg-block-rollback@example.test", "phone"));
        doThrow(new IllegalStateException("fixture revocation unavailable")).when(refreshRecords)
                .revokeAccount(eq(id), eq(com.delivery.auth_service.entity.RefreshTokenRecord.State.REVOKED), any());
        assertThatThrownBy(() -> lifecycle.block(id, 99L, "reason")).hasMessage("fixture revocation unavailable");
        var persisted = accounts.findById(id).orElseThrow();
        assertThat(persisted.getIsActive()).isTrue();
        assertThat(persisted.getLifecycleStatus()).isEqualTo(com.delivery.identity.contracts.IdentityLifecycleStatus.ACTIVE);
        assertThat(persisted.getLifecycleVersion()).isZero();
        assertThat(persisted.getUserStatusSyncPending()).isFalse();
        assertThat(devices.activeSessions("pg-block-rollback@example.test")).hasSize(1);
        verifyNoInteractions(userStatusProjection);
    }

    @Test void afterCommitProjectionFailureLeavesCommittedBlockPendingForRetry() {
        Long id = activeAccount("pg-block-retry@example.test", 9503L);
        doThrow(new IllegalStateException("fixture User unavailable")).when(userStatusProjection)
                .synchronize(9503L, 99L, "reason", true);
        assertThatThrownBy(() -> lifecycle.block(id, 99L, "reason")).hasMessage("fixture User unavailable");
        var persisted = accounts.findById(id).orElseThrow();
        assertThat(persisted.getIsActive()).isFalse();
        assertThat(persisted.getUserStatusSyncPending()).isTrue();
        // Failure metadata must survive while the post-commit error is still reported.
        assertThat(persisted.getUserStatusSyncAttempts()).isEqualTo(1);
        assertThat(persisted.getUserStatusSyncLastError()).isEqualTo("fixture User unavailable");
        doNothing().when(userStatusProjection).synchronize(9503L, 99L, "reason", true);
        lifecycle.reconcile();
        assertThat(accounts.findById(id).orElseThrow().getUserStatusSyncPending()).isFalse();
        verify(userStatusProjection, times(2)).synchronize(9503L, 99L, "reason", true);
    }

    @Test void staleProjectionAcknowledgementCannotClearANewerStatusVersion() {
        Long id = activeAccount("pg-block-stale@example.test", 9504L);
        doAnswer(call -> {
            sql.update("update auth_account set user_status_sync_version=user_status_sync_version+1 where id=?",id);
            return null;
        }).when(userStatusProjection).synchronize(9504L, 99L, "reason", true);
        lifecycle.block(id, 99L, "reason");
        var persisted = accounts.findById(id).orElseThrow();
        assertThat(persisted.getUserStatusSyncVersion()).isEqualTo(2L);
        assertThat(persisted.getUserStatusSyncPending()).isTrue();
        doNothing().when(userStatusProjection).synchronize(9504L, 99L, "reason", true);
        lifecycle.reconcile();
        assertThat(accounts.findById(id).orElseThrow().getUserStatusSyncPending()).isFalse();
    }

    @Test void unblockingUsesPersistedOnboardingStateWithoutReactivatingDeviceSessions() {
        Long id = activeAccount("pg-unblock@example.test", 9505L);
        login.login(loginCommand("pg-unblock@example.test", "phone"));
        lifecycle.block(id, 99L, "reason");
        lifecycle.unblock(id, 99L);
        var persisted = accounts.findById(id).orElseThrow();
        assertThat(persisted.getIsActive()).isTrue();
        assertThat(persisted.getLifecycleStatus()).isEqualTo(com.delivery.identity.contracts.IdentityLifecycleStatus.ACTIVE);
        assertThat(persisted.getLifecycleVersion()).isEqualTo(2L);
        assertThat(devices.activeSessions("pg-unblock@example.test")).isEmpty();
        verify(userStatusProjection).synchronize(9505L, 99L, null, false);
        var unlinked = registration.register(command("pg-unlinked-unblock@example.test", "Password1!", "USER")).account().id();
        lifecycle.block(unlinked, 99L, "reason"); lifecycle.unblock(unlinked, 99L);
        assertThat(accounts.findById(unlinked).orElseThrow().getLifecycleStatus())
                .isEqualTo(com.delivery.identity.contracts.IdentityLifecycleStatus.PENDING_PROFILE);
        sql.update("update auth_account set user_id=? where id=?",9506L,unlinked);
        lifecycle.block(unlinked, 99L, "reason"); lifecycle.unblock(unlinked, 99L);
        assertThat(accounts.findById(unlinked).orElseThrow().getLifecycleStatus())
                .isEqualTo(com.delivery.identity.contracts.IdentityLifecycleStatus.PENDING_EMAIL_VERIFICATION);
    }

    @Test void eventModeWritesStatusOutboxAtomicallyWithoutHttpProjectionOrLegacyPendingMutation() throws Exception {
        Long id = activeAccount("pg-block-events@example.test", 9507L);
        sql.update("update auth_account set user_status_sync_pending=true,user_status_sync_version=8 where id=?",id);
        var persistence = new JpaAccountLifecycleAdapter(accounts, sessions, refreshRecords,
                new IdentityStatusOutboxService(outboxEvents, mapper, true));
        var core = new com.delivery.auth.application.DefaultAccountLifecycleUseCase(accountFlow, persistence,
                transactions, lifecycleTransactions, userStatusProjection, lifecycleTelemetry, true);
        core.block(id, 99L, "reason"); core.reconcile();
        var persisted = accounts.findById(id).orElseThrow();
        assertThat(persisted.getUserStatusSyncVersion()).isEqualTo(8L);
        assertThat(persisted.getUserStatusSyncPending()).isTrue();
        var event = outboxEvents.findAll().stream().filter(row -> row.getAggregateId().equals(id)).findFirst().orElseThrow();
        var payload = mapper.readValue(event.getPayload(), com.delivery.identity.contracts.IdentityStatusChanged.class);
        assertThat(payload.principalId()).isEqualTo(id);
        assertThat(payload.status()).isEqualTo(com.delivery.identity.contracts.IdentityLifecycleStatus.BLOCKED);
        assertThat(payload.lifecycleVersion()).isEqualTo(1L);
        assertThat(payload.changedByPrincipalId()).isEqualTo(99L);
        verifyNoInteractions(userStatusProjection);
    }

    @Test void outboxFailureRollsBackEventModeAccountMutationAndCredentialRevocation() {
        Long id = activeAccount("pg-block-outbox-rollback@example.test", 9508L);
        login.login(loginCommand("pg-block-outbox-rollback@example.test", "phone"));
        var persistence = new JpaAccountLifecycleAdapter(accounts, sessions, refreshRecords,
                new IdentityStatusOutboxService(outboxEvents, mapper, true));
        var core = new com.delivery.auth.application.DefaultAccountLifecycleUseCase(accountFlow, persistence,
                transactions, lifecycleTransactions, userStatusProjection, lifecycleTelemetry, true);
        doThrow(new IllegalStateException("fixture outbox unavailable")).when(outboxEvents).save(any());
        assertThatThrownBy(() -> core.block(id, 99L, "reason")).hasMessage("fixture outbox unavailable");
        assertThat(accounts.findById(id).orElseThrow().getIsActive()).isTrue();
        assertThat(accounts.findById(id).orElseThrow().getLifecycleVersion()).isZero();
        assertThat(devices.activeSessions("pg-block-outbox-rollback@example.test")).hasSize(1);
        assertThat(sql.queryForObject("select count(*) from identity_outbox_events where aggregate_id=?",Long.class,id)).isZero();
        assertThat(sql.queryForObject("select count(*) from auth_refresh_token t join auth_session s on s.id=t.session_id where s.auth_id=? and t.state='CURRENT'",Long.class,id)).isEqualTo(1L);
        verifyNoInteractions(userStatusProjection);
    }

    @Test void simulationBindingSignsThePersistedFenceAndPreventsRunTakeover() throws Exception {
        Long id=activeAccount("pg-simulation@example.test",9701L);
        var entity=accounts.findById(id).orElseThrow();entity.setSimulationActor(true);accounts.saveAndFlush(entity);
        var run=java.util.UUID.randomUUID();var cohort=java.util.UUID.randomUUID();
        var issued=simulationBindings.bindAndIssueAccessToken(id,run,cohort);
        assertThat(issued.binding().bindingVersion()).isEqualTo(1L);
        var jwt=com.nimbusds.jwt.SignedJWT.parse(issued.accessToken());
        var jwks=com.nimbusds.jose.jwk.JWKSet.parse(tokens.getJwks());
        var key=(com.nimbusds.jose.jwk.RSAKey)jwks.getKeyByKeyId(jwt.getHeader().getKeyID());
        assertThat(jwt.verify(new com.nimbusds.jose.crypto.RSASSAVerifier(key.toRSAPublicKey()))).isTrue();
        assertThat(jwt.getJWTClaimsSet().getLongClaim("principal_id")).isEqualTo(id);
        assertThat(jwt.getJWTClaimsSet().getStringClaim("simulation_mode")).isEqualTo("SIMULATION");
        assertThat(jwt.getJWTClaimsSet().getStringClaim("simulation_run_id")).isEqualTo(run.toString());
        assertThat(jwt.getJWTClaimsSet().getStringClaim("simulation_cohort_id")).isEqualTo(cohort.toString());
        assertThat(jwt.getJWTClaimsSet().getLongClaim("simulation_binding_version")).isEqualTo(1L);
        assertThatThrownBy(()->simulationBindings.bind(id,java.util.UUID.randomUUID(),cohort))
                .hasMessageContaining("another simulation run");
        assertThatThrownBy(()->simulationBindings.bind(id,run,java.util.UUID.randomUUID()))
                .hasMessageContaining("another cohort");
        assertThatThrownBy(()->simulationBindings.unbind(id,run,2L)).hasMessage("Simulation binding fence does not match");
        simulationBindings.unbind(id,run,1L);
        var reloaded=accounts.findById(id).orElseThrow();
        assertThat(reloaded.getActiveSimulationRunId()).isNull();
        assertThat(reloaded.getSimulationCohortId()).isEqualTo(cohort);
        assertThat(reloaded.getSimulationBindingVersion()).isEqualTo(2L);
    }

    @Test void simulationTokenIssuerFailureRollsBackTheNewLease() {
        Long id=activeAccount("pg-simulation-rollback@example.test",9702L);
        var entity=accounts.findById(id).orElseThrow();entity.setSimulationActor(true);accounts.saveAndFlush(entity);
        var failed=new com.delivery.auth.application.DefaultSimulationBindingUseCase(lockedAccounts,transactions,
                (account,binding)->{throw new IllegalStateException("fixture signing unavailable");});
        assertThatThrownBy(()->failed.bindAndIssueAccessToken(id,java.util.UUID.randomUUID(),java.util.UUID.randomUUID()))
                .hasMessage("fixture signing unavailable");
        var reloaded=accounts.findById(id).orElseThrow();
        assertThat(reloaded.getActiveSimulationRunId()).isNull();
        assertThat(reloaded.getSimulationCohortId()).isNull();
        assertThat(reloaded.getSimulationBindingVersion()).isZero();
    }

    @Test void registrationPersistsIdentityAndRecoveryAndSignsTheAuthHandoff() throws Exception {
        var first = registration.register(command("pg-owner@example.test", "Password1!", "USER"));
        var persisted = accounts.findById(first.account().id()).orElseThrow();
        assertThat(persisted.getUserId()).isNull();
        assertThat(persisted.getEmailVerificationRequired()).isTrue();
        assertThat(passwords.matches("Password1!", persisted.getPasswordHash())).isTrue();
        var replay = registration.register(command(" PG-OWNER@example.test ", "Password1!", "CUSTOMER"));
        assertThat(replay.account().id()).isEqualTo(first.account().id());
        var jwt = com.nimbusds.jwt.SignedJWT.parse(first.provisioningToken());
        var jwks = com.nimbusds.jose.jwk.JWKSet.parse(tokens.getJwks());
        var key = (com.nimbusds.jose.jwk.RSAKey) jwks.getKeyByKeyId(jwt.getHeader().getKeyID());
        assertThat(jwt.verify(new com.nimbusds.jose.crypto.RSASSAVerifier(key.toRSAPublicKey()))).isTrue();
        assertThat(jwt.getJWTClaimsSet().getLongClaim("principal_id")).isEqualTo(first.account().id());
        assertThat(jwt.getJWTClaimsSet().getStringClaim("token_type")).isEqualTo("provisioning");
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("delivery-user-registration");
        assertThat(statuses.status(first.registrationHandle()).nextAction()).isEqualTo("CREATE_PROFILE");
        assertThat(first.registrationHandleExpiresAt()).isAfter(java.time.LocalDateTime.now().plusMinutes(14));
    }

    @Test void simultaneousPasswordRegistrationsConvergeOnOneIdentity() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<RegistrationResult> task = () -> { start.await(); return registration.register(command("pg-race@example.test", "Password1!", "USER")); };
            var first = pool.submit(task); var second = pool.submit(task); start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS).account().id()).isEqualTo(second.get(15, TimeUnit.SECONDS).account().id());
            assertThat(accounts.findAll().stream().filter(row -> row.getEmail().equals("pg-race@example.test")).count()).isEqualTo(1L);
        } finally { pool.shutdownNow(); }
    }

    @Test void retryCannotTakeOverAnotherPasswordOrRoleAndBlockedIdentityStaysRejected() {
        var first = registration.register(command("pg-protected@example.test", "Password1!", "USER"));
        assertThatThrownBy(() -> registration.register(command("pg-protected@example.test", "WrongPassword", "USER")))
                .isInstanceOf(com.delivery.auth.domain.policy.RegistrationIdentityConflict.class);
        assertThatThrownBy(() -> registration.register(command("pg-protected@example.test", "Password1!", "SHOP_OWNER")))
                .isInstanceOf(com.delivery.auth.domain.policy.RegistrationIdentityConflict.class);
        var entity = accounts.findById(first.account().id()).orElseThrow();
        entity.setIsActive(false); accounts.saveAndFlush(entity);
        assertThatThrownBy(() -> registration.register(command("pg-protected@example.test", "Password1!", "USER")))
                .isInstanceOf(com.delivery.auth.domain.policy.CredentialsRejected.class);
    }

    @Test void recoveryHandleFailureLeavesTheCommittedIdentityResumable() {
        doThrow(new IllegalStateException("fixture handle store unavailable")).when(handles).save(any());
        assertThatThrownBy(() -> registration.register(command("pg-recovery@example.test", "Password1!", "USER")))
                .isInstanceOf(IllegalStateException.class);
        var committed = accounts.findByEmail("pg-recovery@example.test").orElseThrow();
        reset(handles);
        assertThat(registration.register(command("pg-recovery@example.test", "Password1!", "USER")).account().id()).isEqualTo(committed.getId());
    }

    @Test void productionHttpRegistrationUsesCoreAndKeepsRecoveryWireShape() throws Exception {
        http.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"email\":\"pg-http@example.test\",\"password\":\"Password1!\",\"role\":\"USER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.authId").isNumber())
                .andExpect(jsonPath("$.data.provisioningToken").isString())
                .andExpect(jsonPath("$.data.registrationHandle").isString());
        assertThat(accounts.findByEmail("pg-http@example.test").orElseThrow().getUserId()).isNull();
    }

    @Test void loginCommitsRefreshCredentialAndPreservesTrustedIdentityAndSimulationClaims() throws Exception {
        Long id = activeAccount("pg-login@example.test", 9001L);
        var entity = accounts.findById(id).orElseThrow();
        java.util.UUID run = java.util.UUID.randomUUID();
        java.util.UUID cohort = java.util.UUID.randomUUID();
        entity.setSimulationActor(true); entity.setActiveSimulationRunId(run); entity.setSimulationCohortId(cohort);
        entity.setSimulationBindingVersion(2L); accounts.saveAndFlush(entity);
        var result = login.login(loginCommand("pg-login@example.test", "device-1"));
        assertThat(result.authId()).isEqualTo(id);
        var claims = com.nimbusds.jwt.SignedJWT.parse(result.accessToken()).getJWTClaimsSet();
        assertThat(claims.getLongClaim("principal_id")).isEqualTo(id);
        assertThat(claims.getLongClaim("legacy_user_id")).isEqualTo(9001L);
        assertThat(claims.getStringClaim("simulation_run_id")).isEqualTo(run.toString());
        assertThat(claims.getStringClaim("simulation_cohort_id")).isEqualTo(cohort.toString());
        assertThat(claims.getLongClaim("simulation_binding_version")).isEqualTo(2L);
        assertThat(tokens.isValid(result.accessToken())).isTrue();
        assertThat(tokens.isValidRefreshToken(result.refreshToken())).isTrue();
        assertThat(sql.queryForObject("select count(*) from auth_refresh_token t join auth_session s on s.id=t.session_id where s.auth_id=? and t.state='CURRENT'", Long.class, id)).isEqualTo(1L);
        String hash = sql.queryForObject("select t.token_hash from auth_refresh_token t join auth_session s on s.id=t.session_id where s.auth_id=?", String.class, id);
        String expected = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(result.refreshToken().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(hash).isEqualTo(expected).hasSize(64).isNotEqualTo(result.refreshToken());
    }

    @Test void sameDeviceLoginRevokesPreviousFamilyAndKeepsOtherDeviceActive() {
        Long id = activeAccount("pg-device@example.test", 9002L);
        login.login(loginCommand("pg-device@example.test", "phone"));
        login.login(loginCommand("pg-device@example.test", "web"));
        login.login(loginCommand("pg-device@example.test", "phone"));
        assertThat(sql.queryForObject("select count(*) from auth_session where auth_id=? and is_active=true", Long.class, id)).isEqualTo(2L);
        assertThat(sql.queryForObject("select count(*) from auth_refresh_token t join auth_session s on s.id=t.session_id where s.auth_id=? and t.state='REVOKED'", Long.class, id)).isEqualTo(1L);
        assertThat(sql.queryForObject("select count(*) from auth_refresh_token t join auth_session s on s.id=t.session_id where s.auth_id=? and t.state='CURRENT'", Long.class, id)).isEqualTo(2L);
    }

    @Test void failedRefreshCredentialWriteRollsBackNewSessionAndPreviousDeviceRevocation() {
        Long id = activeAccount("pg-login-rollback@example.test", 9003L);
        login.login(loginCommand("pg-login-rollback@example.test", "phone"));
        doThrow(new IllegalStateException("fixture refresh store unavailable")).when(refreshRecords).save(any());
        assertThatThrownBy(() -> login.login(loginCommand("pg-login-rollback@example.test", "phone")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(sql.queryForObject("select count(*) from auth_session where auth_id=?", Long.class, id)).isEqualTo(1L);
        assertThat(sql.queryForObject("select count(*) from auth_session where auth_id=? and is_active=true", Long.class, id)).isEqualTo(1L);
        assertThat(sql.queryForObject("select count(*) from auth_refresh_token t join auth_session s on s.id=t.session_id where s.auth_id=? and t.state='CURRENT'", Long.class, id)).isEqualTo(1L);
    }

    @Test void productionLoginHttpUsesCoreAndPreservesAuthenticationEnvelope() throws Exception {
        Long id = activeAccount("pg-login-http@example.test", 9004L);
        http.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"pg-login-http@example.test\",\"password\":\"Password1!\",\"deviceId\":\"phone\",\"deviceType\":\"mobile\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.authId").value(id))
                .andExpect(jsonPath("$.data.accessToken").isString()).andExpect(jsonPath("$.data.refreshToken").isString());
        http.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"pg-login-http@example.test\",\"password\":\"WrongPassword\",\"deviceId\":\"phone\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test void concurrentRefreshCommitsOneRotationAndThenRevokesTheEntireFamilyOnReplay() throws Exception {
        Long id = activeAccount("pg-refresh-race@example.test", 9011L);
        var issued = login.login(loginCommand("pg-refresh-race@example.test", "phone"));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Object> rotate = () -> {
                start.await();
                try { return refresh.refresh(new RefreshTokenCommand(issued.refreshToken())); }
                catch (com.delivery.auth.domain.policy.RefreshCredentialReuse reuse) { return reuse; }
            };
            var a = pool.submit(rotate); var b = pool.submit(rotate); start.countDown();
            var results = java.util.List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            assertThat(results).filteredOn(AuthenticationResult.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(com.delivery.auth.domain.policy.RefreshCredentialReuse.class::isInstance).hasSize(1);
            var successor = (AuthenticationResult) results.stream().filter(AuthenticationResult.class::isInstance).findFirst().orElseThrow();
            assertThatThrownBy(() -> refresh.refresh(new RefreshTokenCommand(successor.refreshToken())))
                    .isInstanceOf(com.delivery.auth.domain.policy.InvalidAuthToken.class);
        } finally { pool.shutdownNow(); }
        assertThat(sql.queryForObject("select count(*) from auth_session where auth_id=? and is_active=true", Long.class, id)).isZero();
        assertThat(sql.queryForObject("select count(*) from auth_refresh_token t join auth_session s on s.id=t.session_id where s.auth_id=? and t.state='REVOKED'", Long.class, id)).isEqualTo(2L);
    }

    @Test void failedRotationSuccessorWriteRollsBackTheConsumedCredentialAndSessionExpiry() {
        Long id = activeAccount("pg-refresh-rollback@example.test", 9012L);
        var issued = login.login(loginCommand("pg-refresh-rollback@example.test", "phone"));
        var before = sql.queryForMap("select last_login_at, expires_at from auth_session where auth_id=?", id);
        doThrow(new IllegalStateException("fixture successor write unavailable")).when(refreshRecords)
                .save(argThat(record -> record != null && record.getId() == null));
        try {
            assertThatThrownBy(() -> refresh.refresh(new RefreshTokenCommand(issued.refreshToken())))
                    .isInstanceOf(IllegalStateException.class).hasMessage("fixture successor write unavailable");
            assertThat(sql.queryForMap("select last_login_at, expires_at from auth_session where auth_id=?", id)).isEqualTo(before);
            assertThat(sql.queryForObject("select count(*) from auth_refresh_token t join auth_session s on s.id=t.session_id where s.auth_id=? and t.state='CURRENT' and t.rotated_at is null", Long.class, id)).isEqualTo(1L);
            assertThat(sql.queryForObject("select count(*) from auth_refresh_token t join auth_session s on s.id=t.session_id where s.auth_id=?", Long.class, id)).isEqualTo(1L);
        } finally { reset(refreshRecords); }
        assertThat(refresh.refresh(new RefreshTokenCommand(issued.refreshToken())).refreshToken()).isNotEqualTo(issued.refreshToken());
    }

    @Test void refreshHttpKeepsEnvelopeAndLogoutRevokesOnlyTheSelectedDevice() throws Exception {
        Long id = activeAccount("pg-refresh-http@example.test", 9013L);
        var phone = login.login(loginCommand("pg-refresh-http@example.test", "phone"));
        var web = login.login(loginCommand("pg-refresh-http@example.test", "web"));
        http.perform(post("/api/auth/refresh-token").contentType("application/json")
                .content("{\"refreshToken\":\"" + phone.refreshToken() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.authId").value(id))
                .andExpect(jsonPath("$.data.accessToken").isString()).andExpect(jsonPath("$.data.refreshToken").isString());
        http.perform(post("/api/auth/refresh-token").contentType("application/json")
                .content("{\"refreshToken\":\"" + phone.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Refresh token reuse detected; device session revoked"));
        assertThat(refresh.refresh(new RefreshTokenCommand(web.refreshToken())).refreshToken()).isNotEqualTo(web.refreshToken());
        logout.logout(web.refreshToken());
        assertThat(sql.queryForObject("select count(*) from auth_session where auth_id=? and is_active=true", Long.class, id)).isZero();
        http.perform(post("/api/auth/refresh-token").contentType("application/json").content("{\"refreshToken\":\"invalid\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Invalid refresh token"));
    }

    @Test void activeSessionsAreFilteredSortedAndBoundedInPostgres() {
        Long id = activeAccount("pg-session-list@example.test", 9021L);
        var account = accounts.findById(id).orElseThrow();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.util.List<com.delivery.auth_service.entity.AuthSession> rows = new java.util.ArrayList<>();
        for (int index = 0; index < 122; index++) {
            var session = new com.delivery.auth_service.entity.AuthSession();
            session.setAuthAccount(account); session.setDeviceId("device-" + index); session.setTokenFamilyId("family-" + index);
            session.setIsActive(index != 120); session.setLastLoginAt(now.minusMinutes(index));
            session.setExpiresAt(index == 121 ? now.minusMinutes(1) : now.plusDays(7)); rows.add(session);
        }
        sessions.saveAllAndFlush(rows);
        var active = devices.activeSessions(" PG-SESSION-LIST@example.test ");
        assertThat(active).hasSize(100);
        assertThat(active.get(0).deviceId()).isEqualTo("device-0");
        assertThat(active.get(99).deviceId()).isEqualTo("device-99");
        assertThat(active).allSatisfy(row -> assertThat(row.isUsableAt(now)).isTrue());
    }

    @Test void deviceRevocationIsAtomicAndPreservesOtherDevices() {
        Long id = activeAccount("pg-session-revoke@example.test", 9022L);
        var phone = login.login(loginCommand("pg-session-revoke@example.test", "phone"));
        var web = login.login(loginCommand("pg-session-revoke@example.test", "web"));
        doThrow(new IllegalStateException("fixture session write unavailable")).when(sessions).save(any());
        try {
            assertThatThrownBy(() -> devices.revokeDevice("pg-session-revoke@example.test", "phone"))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(devices.activeSessions("pg-session-revoke@example.test")).hasSize(2);
            assertThat(sql.queryForObject("select count(*) from auth_refresh_token t join auth_session s on s.id=t.session_id where s.auth_id=? and t.state='CURRENT'", Long.class, id)).isEqualTo(2L);
        } finally { reset(sessions); }
        devices.revokeDevice("pg-session-revoke@example.test", " phone ");
        assertThat(devices.activeSessions("pg-session-revoke@example.test")).singleElement()
                .satisfies(row -> assertThat(row.deviceId()).isEqualTo("web"));
        assertThatThrownBy(() -> refresh.refresh(new RefreshTokenCommand(phone.refreshToken())))
                .isInstanceOf(com.delivery.auth.domain.policy.RefreshCredentialReuse.class);
        assertThat(refresh.refresh(new RefreshTokenCommand(web.refreshToken())).refreshToken()).isNotBlank();
        devices.revokeDevice("pg-session-revoke@example.test", "missing");
    }

    @Test void recoveryHttpKeepsStatusHeadersAndUnknownAndExpiredHandleErrors() throws Exception {
        var issued = registration.register(command("pg-recovery-http@example.test", "Password1!", "USER"));
        http.perform(get("/api/auth/registrations/" + issued.registrationHandle()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Retry-After", "3"))
                .andExpect(jsonPath("$.data.principalId").value(issued.account().id()))
                .andExpect(jsonPath("$.data.status").value("PENDING_PROFILE"))
                .andExpect(jsonPath("$.data.nextAction").value("CREATE_PROFILE"))
                .andExpect(jsonPath("$.data.profileLinked").value(false));
        http.perform(get("/api/auth/registrations/unknown-handle"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Registration not found with handle: not found"));
        sql.update("update identity_registrations set expires_at=? where auth_account_id=?",
                java.time.LocalDateTime.now().minusSeconds(1), issued.account().id());
        http.perform(get("/api/auth/registrations/" + issued.registrationHandle()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Registration handle expired"));
    }

    @Test void recoveryCleanupDeletesOnlyHandlesPastTheConfiguredRetention() {
        var old = registration.register(command("pg-handle-old@example.test", "Password1!", "USER"));
        var recent = registration.register(command("pg-handle-recent@example.test", "Password1!", "USER"));
        var live = registration.register(command("pg-handle-live@example.test", "Password1!", "USER"));
        sql.update("update identity_registrations set expires_at=? where auth_account_id=?", java.time.LocalDateTime.now().minusDays(2),old.account().id());
        sql.update("update identity_registrations set expires_at=? where auth_account_id=?", java.time.LocalDateTime.now().minusHours(12),recent.account().id());
        statuses.cleanupExpiredHandles();
        assertThat(sql.queryForObject("select count(*) from identity_registrations where auth_account_id=?",Long.class,old.account().id())).isZero();
        assertThat(sql.queryForObject("select count(*) from identity_registrations where auth_account_id=?",Long.class,recent.account().id())).isEqualTo(1L);
        assertThat(statuses.status(live.registrationHandle()).nextAction()).isEqualTo("CREATE_PROFILE");
        assertThat(accounts.findById(old.account().id())).isPresent();
    }

    @Test void operatorAdminProvisioningPersistsVerifiedCredentialsAndRetryKeepsTheSameLinkedIdentity() throws Exception {
        when(profiles.provision(any())).thenAnswer(call -> {
            com.delivery.auth.domain.model.AuthAccount account=call.getArgument(0);
            return new UserProfileProvisioningReply(1,"ok",9101L,account.id(),account.email(),account.role().name());
        });
        var first=operators.provisionAdmin(" PG-OPERATOR-ADMIN@example.test ","Password1!");
        var retry=operators.provisionAdmin("pg-operator-admin@example.test","Password1!");
        assertThat(retry.id()).isEqualTo(first.id());assertThat(retry.userId()).isEqualTo(9101L);
        assertThat(retry.lifecycleStatus()).isEqualTo(com.delivery.auth.domain.model.AuthAccount.LifecycleStatus.ACTIVE);
        assertThat(retry.lifecycleVersion()).isEqualTo(1L);assertThat(retry.emailVerifiedAt()).isNotNull();
        assertThat(retry.emailVerificationRequired()).isFalse();verify(profiles,times(1)).provision(any());
        var session=login.login(loginCommand("pg-operator-admin@example.test","operator-web"));
        assertThat(com.nimbusds.jwt.SignedJWT.parse(session.accessToken()).getJWTClaimsSet().getStringClaim("role")).isEqualTo("ADMIN");
        assertThatThrownBy(() -> operators.provisionAdmin("pg-operator-admin@example.test","WrongPassword"))
                .isInstanceOf(com.delivery.auth.domain.policy.RegistrationIdentityConflict.class);
    }

    @Test void rejectedOperatorHandoffLeavesTheCommittedIdentityResumableWithoutAConflictingBinding() {
        when(profiles.provision(any())).thenAnswer(call -> {
            com.delivery.auth.domain.model.AuthAccount account=call.getArgument(0);
            return new UserProfileProvisioningReply(1,"ok",9102L,account.id()+1,account.email(),account.role().name());
        });
        assertThatThrownBy(() -> operators.provisionShipper("pg-operator-shipper@example.test","Password1!"))
                .hasMessage("User service returned a conflicting provisioning identity");
        var pending=accounts.findByEmail("pg-operator-shipper@example.test").orElseThrow();
        assertThat(pending.getUserId()).isNull();assertThat(pending.getLifecycleVersion()).isZero();
        reset(profiles);
        when(profiles.provision(any())).thenAnswer(call -> {
            com.delivery.auth.domain.model.AuthAccount account=call.getArgument(0);
            return new UserProfileProvisioningReply(1,"ok",9102L,account.id(),account.email(),account.role().name());
        });
        var resumed=operators.provisionShipper("pg-operator-shipper@example.test","Password1!");
        assertThat(resumed.id()).isEqualTo(pending.getId());assertThat(resumed.userId()).isEqualTo(9102L);
        assertThat(resumed.role()).isEqualTo(com.delivery.auth.domain.model.AuthAccount.Role.SHIPPER);
    }

    @Test void socialHttpUsesCoreAndKeepsTrustedClaimsAndLegacyDeviceTypeFallback() throws Exception {
        allowSocial("pg-social-http@example.test",9301L);
        http.perform(post("/api/auth/social-login").contentType("application/json")
                .content("{\"provider\":\"google\",\"token\":\"fixture\",\"role\":\"USER\",\"deviceId\":\"phone\",\"deviceType\":\"unknown\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.refreshToken").isString());
        var account=accounts.findByEmail("pg-social-http@example.test").orElseThrow();
        assertThat(account.getUserId()).isEqualTo(9301L);assertThat(account.getEmailVerificationRequired()).isFalse();
        assertThat(account.getLifecycleStatus()).isEqualTo(com.delivery.identity.contracts.IdentityLifecycleStatus.ACTIVE);
        assertThat(sql.queryForObject("select device_type from auth_session where auth_id=?",String.class,account.getId())).isEqualTo("mobile");
        var result=social.login(socialCommand("ADMIN","web")); // Existing role ignores client-requested elevation.
        var claims=com.nimbusds.jwt.SignedJWT.parse(result.accessToken()).getJWTClaimsSet();
        assertThat(claims.getLongClaim("principal_id")).isEqualTo(account.getId());
        assertThat(claims.getLongClaim("legacy_user_id")).isEqualTo(9301L);assertThat(claims.getStringClaim("role")).isEqualTo("USER");
        assertThat(tokens.isValid(result.accessToken())).isTrue();
        assertThat(refresh.refresh(new RefreshTokenCommand(result.refreshToken())).refreshToken()).isNotEqualTo(result.refreshToken());
    }

    @Test void socialCredentialFailureRollsBackVerificationAndDeviceRevocationAndNewSession() {
        Long id=activeAccount("pg-social-rollback@example.test",9302L);
        login.login(loginCommand("pg-social-rollback@example.test","phone"));
        var account=accounts.findById(id).orElseThrow();account.setEmailVerifiedAt(null);account.setEmailVerificationRequired(true);accounts.saveAndFlush(account);
        allowSocial("pg-social-rollback@example.test",9302L);
        doThrow(new IllegalStateException("fixture social credential unavailable")).when(refreshRecords).save(any());
        assertThatThrownBy(() -> social.login(socialCommand(null,"phone"))).hasMessage("fixture social credential unavailable");
        var after=accounts.findById(id).orElseThrow();assertThat(after.getEmailVerifiedAt()).isNull();assertThat(after.getEmailVerificationRequired()).isTrue();
        assertThat(sql.queryForObject("select count(*) from auth_session where auth_id=? and is_active=true",Long.class,id)).isEqualTo(1L);
        assertThat(sql.queryForObject("select count(*) from auth_session where auth_id=?",Long.class,id)).isEqualTo(1L);
        assertThat(sql.queryForObject("select count(*) from auth_refresh_token t join auth_session s on s.id=t.session_id where s.auth_id=? and t.state='CURRENT'",Long.class,id)).isEqualTo(1L);
    }

    @Test void failedNewSocialLoginRollsBackTheLocalIdentityAndBinding() {
        allowSocial("pg-social-new-rollback@example.test",9303L);
        doThrow(new IllegalStateException("fixture social credential unavailable")).when(refreshRecords).save(any());
        assertThatThrownBy(() -> social.login(socialCommand(null,null))).hasMessage("fixture social credential unavailable");
        assertThat(accounts.findByEmail("pg-social-new-rollback@example.test")).isEmpty();
        verify(profiles).provision(any()); // Remote handoff itself is not a distributed transaction.
    }

    @Test void socialCannotManufactureOperatorRoleButRetainsAnExistingOperatorIdentity() {
        allowSocial("pg-social-shipper@example.test",9304L);
        assertThatThrownBy(() -> social.login(socialCommand("SHIPPER","phone"))).hasMessageContaining("operator provisioning");
        assertThat(accounts.findByEmail("pg-social-shipper@example.test")).isEmpty();
        var operator=operators.provisionShipper("pg-social-shipper@example.test","Password1!");
        var result=social.login(socialCommand("ADMIN","phone"));
        assertThat(result.authId()).isEqualTo(operator.id());assertThat(result.role()).isEqualTo("SHIPPER");
    }

    @Test void simultaneousNewSocialLoginsConvergeWithoutPoisoningTheOuterTransaction() throws Exception {
        allowSocial("pg-social-race@example.test",9305L);
        CyclicBarrier bothReadMissing=new CyclicBarrier(2);
        AuthAccountPort racingAccounts=new AuthAccountPort() {
            public java.util.Optional<com.delivery.auth.domain.model.AuthAccount> findByEmail(String email) {
                var found=accountFlow.findByEmail(email);
                try { bothReadMissing.await(10,TimeUnit.SECONDS); } catch(Exception e) { throw new IllegalStateException(e); }
                return found;
            }
            public java.util.Optional<com.delivery.auth.domain.model.AuthAccount> findById(Long id) { return accountFlow.findById(id); }
            public com.delivery.auth.domain.model.AuthAccount save(com.delivery.auth.domain.model.AuthAccount account) { return accountFlow.save(account); }
            public com.delivery.auth.domain.model.AuthAccount createOrResume(com.delivery.auth.domain.model.AuthAccount account,
                    java.util.function.Consumer<com.delivery.auth.domain.model.AuthAccount> verify) { return accountFlow.createOrResume(account,verify); }
        };
        var core=new com.delivery.auth.application.DefaultSocialLoginUseCase(transactions,socialIdentities,racingAccounts,
                credentials,profileFlow,sessionFlow,sessionTokens,refreshFlow);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(() -> core.login(socialCommand("USER","phone")));
            var second=pool.submit(() -> core.login(socialCommand("SHOP_OWNER","web")));
            var a=first.get(15,TimeUnit.SECONDS);var b=second.get(15,TimeUnit.SECONDS);
            assertThat(a.authId()).isEqualTo(b.authId());assertThat(a.role()).isEqualTo(b.role());
            assertThat(tokens.isValid(a.accessToken())).isTrue();assertThat(tokens.isValid(b.accessToken())).isTrue();
            assertThat(devices.activeSessions("pg-social-race@example.test")).hasSize(2);
        } finally { pool.shutdownNow(); }
        assertThat(accounts.findAll().stream().filter(row -> row.getEmail().equals("pg-social-race@example.test")).count()).isEqualTo(1L);
    }

    private void allowSocial(String email,Long userId) {
        when(socialIdentities.verify(any(),any())).thenReturn(java.util.Optional.of(new SocialIdentityPort.VerifiedSocialIdentity("google",email,true)));
        when(profiles.provision(any())).thenAnswer(call -> {
            com.delivery.auth.domain.model.AuthAccount account=call.getArgument(0);
            return new UserProfileProvisioningReply(1,"ok",userId,account.id(),account.email(),account.role().name());
        });
    }
    private SocialLoginCommand socialCommand(String role,String device) {
        return new SocialLoginCommand("google","fixture",role,device,"Fixture",com.delivery.auth.domain.model.Session.DeviceType.WEB,"ip");
    }

    private Long activeAccount(String email, Long userId) {
        Long id = registration.register(command(email, "Password1!", "USER")).account().id();
        var account = accounts.findById(id).orElseThrow(); account.setUserId(userId);
        account.setEmailVerifiedAt(java.time.LocalDateTime.now());
        account.setLifecycleStatus(com.delivery.identity.contracts.IdentityLifecycleStatus.ACTIVE);
        accounts.saveAndFlush(account); return id;
    }
    private LoginCommand loginCommand(String email, String device) {
        return new LoginCommand(email, "Password1!", device, "Fixture", null, "127.0.0.1");
    }

    private RegisterCommand command(String email, String password, String role) {
        return new RegisterCommand(email, password, role);
    }
}
