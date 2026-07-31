package com.delivery.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;

import com.delivery.auth_service.TestJwtKeyProperties;
import com.delivery.auth_service.dto.AuthResponse;
import com.delivery.auth_service.dto.LoginRequest;
import com.delivery.auth_service.dto.RefreshTokenRequest;
import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.entity.AuthSession;
import com.delivery.auth_service.entity.RefreshTokenRecord;
import com.delivery.auth_service.exception.InvalidTokenException;
import com.delivery.auth_service.exception.RefreshTokenReuseException;
import com.delivery.auth_service.repository.AuthAccountRepository;
import com.delivery.auth_service.repository.AuthSessionRepository;
import com.delivery.auth_service.repository.RefreshTokenRecordRepository;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_refresh_rotation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "INTERNAL_SECRET=test-secret"
})
@ActiveProfiles("test")
class AuthRefreshTokenRotationIntegrationTest {

    @DynamicPropertySource
    static void jwtKeyProperties(DynamicPropertyRegistry registry) {
        TestJwtKeyProperties.register(registry);
    }

    @Autowired
    private AuthService authService;

    @Autowired
    private AuthAccountRepository accountRepository;

    @Autowired
    private AuthSessionRepository sessionRepository;

    @Autowired
    private RefreshTokenRecordRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private RestTemplate restTemplate;

    @MockBean
    private GoogleTokenVerifier googleTokenVerifier;

    @AfterEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAll();
        sessionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void singleRefreshConsumesOldTokenAndCreatesOneUniqueSuccessor() {
        AuthResponse login = login("single@example.com", "phone-1");

        AuthResponse rotated = authService.refreshToken(request(login.getRefreshToken()));

        assertThat(rotated.getAccessToken()).isNotBlank();
        assertThat(rotated.getRefreshToken())
                .isNotBlank()
                .isNotEqualTo(login.getRefreshToken());
        List<RefreshTokenRecord> records = refreshTokenRepository.findAll();
        assertThat(records).hasSize(2);
        assertThat(records).extracting(RefreshTokenRecord::getState)
                .containsExactlyInAnyOrder(
                        RefreshTokenRecord.State.ROTATED,
                        RefreshTokenRecord.State.CURRENT);
        assertThat(records).extracting(RefreshTokenRecord::getTokenHash)
                .doesNotHaveDuplicates()
                .allSatisfy(hash -> assertThat(hash).hasSize(64));
    }

    @Test
    void consumedTokenReuseCommitsRevocationForTheWholeDeviceFamily() {
        AuthResponse login = login("reuse@example.com", "phone-1");
        AuthResponse rotated = authService.refreshToken(request(login.getRefreshToken()));

        assertThatThrownBy(() -> authService.refreshToken(request(login.getRefreshToken())))
                .isInstanceOf(RefreshTokenReuseException.class)
                .hasMessageContaining("reuse detected");

        AuthSession session = sessionRepository.findAll().get(0);
        assertThat(session.getIsActive()).isFalse();
        assertThat(refreshTokenRepository.findAll())
                .allSatisfy(token -> assertThat(token.getState())
                        .isEqualTo(RefreshTokenRecord.State.REVOKED));
        assertThatThrownBy(() -> authService.refreshToken(request(rotated.getRefreshToken())))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void concurrentRefreshSerializesAndReplayRevokesParallelDescendant() throws Exception {
        AuthResponse login = login("race@example.com", "phone-1");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> refreshAfterBarrier(login.getRefreshToken(), ready, start));
            Future<Object> second = executor.submit(() -> refreshAfterBarrier(login.getRefreshToken(), ready, start));
            ready.await();
            start.countDown();

            List<Object> results = List.of(first.get(), second.get());
            assertThat(results).filteredOn(AuthResponse.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(RefreshTokenReuseException.class::isInstance).hasSize(1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(sessionRepository.findAll()).singleElement()
                .satisfies(session -> assertThat(session.getIsActive()).isFalse());
        assertThat(refreshTokenRepository.findAll())
                .hasSize(2)
                .allSatisfy(token -> assertThat(token.getState())
                        .isEqualTo(RefreshTokenRecord.State.REVOKED));
    }

    @Test
    void logoutAndDeviceRevokeDoNotTerminateAnotherDeviceFamily() {
        AuthResponse phone = login("devices@example.com", "phone-1");
        AuthResponse web = login("devices@example.com", "web-1");

        authService.logout(phone.getRefreshToken());
        AuthResponse rotatedWeb = authService.refreshToken(request(web.getRefreshToken()));
        assertThat(rotatedWeb.getRefreshToken()).isNotEqualTo(web.getRefreshToken());

        authService.revokeDeviceSession("devices@example.com", "web-1");
        assertThatThrownBy(() -> authService.refreshToken(request(rotatedWeb.getRefreshToken())))
                .isInstanceOf(InvalidTokenException.class);

        assertThat(sessionRepository.findAll())
                .hasSize(2)
                .allSatisfy(session -> assertThat(session.getIsActive()).isFalse());
    }

    private Object refreshAfterBarrier(String refreshToken, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            return authService.refreshToken(request(refreshToken));
        } catch (RefreshTokenReuseException reuse) {
            return reuse;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private AuthResponse login(String email, String deviceId) {
        AuthAccount account = accountRepository.findByEmail(email).orElseGet(() -> {
            AuthAccount created = new AuthAccount();
            created.setUserId((long) Math.abs(email.hashCode()) + 1L);
            created.setEmail(email);
            created.setPasswordHash(passwordEncoder.encode("Password123!"));
            created.setRole(AuthAccount.Role.USER);
            created.setIsActive(true);
            return accountRepository.saveAndFlush(created);
        });

        LoginRequest request = new LoginRequest();
        request.setEmail(account.getEmail());
        request.setPassword("Password123!");
        request.setDeviceId(deviceId);
        request.setDeviceType(AuthSession.DeviceType.MOBILE);
        return authService.login(request);
    }

    private RefreshTokenRequest request(String refreshToken) {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshToken);
        return request;
    }
}
