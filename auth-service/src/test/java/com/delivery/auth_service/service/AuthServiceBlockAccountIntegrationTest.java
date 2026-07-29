package com.delivery.auth_service.service;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;

import com.delivery.auth_service.TestJwtKeyProperties;
import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.entity.AuthSession;
import com.delivery.auth_service.payload.BaseResponse;
import com.delivery.auth_service.repository.AuthAccountRepository;
import com.delivery.auth_service.repository.AuthSessionRepository;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_block_it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class AuthServiceBlockAccountIntegrationTest {

    @DynamicPropertySource
    static void jwtKeyProperties(DynamicPropertyRegistry registry) {
        TestJwtKeyProperties.register(registry);
    }

    @Autowired
    private AuthService authService;

    @Autowired
    private AuthAccountRepository authAccountRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private TokenService tokenService;

    @MockBean
    private RestTemplate restTemplate;

    @MockBean
    private GoogleTokenVerifier googleTokenVerifier;

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void blockAccountDeactivatesSessionsAndClearsProjectionSyncPendingAfterCommit() {
        AuthAccount account = new AuthAccount();
        account.setEmail("admin@example.com");
        account.setPasswordHash("hash");
        account.setRole(AuthAccount.Role.ADMIN);
        account.setIsActive(true);
        account.setUserId(77L);
        account = authAccountRepository.saveAndFlush(account);

        AuthSession session = new AuthSession();
        session.setAuthAccount(account);
        session.setDeviceId("admin-phone");
        session.setRefreshToken("refresh-token");
        session.setIsActive(true);
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        authSessionRepository.saveAndFlush(session);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(BaseResponse.success(null, "ok")));

        authService.blockAccount(account.getId(), 99L, "fraud review");

        AuthAccount reloadedAccount = authAccountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloadedAccount.getIsActive()).isFalse();
        assertThat(reloadedAccount.getUserStatusSyncPending()).isFalse();
        assertThat(reloadedAccount.getUserStatusSyncAdminId()).isNull();
        assertThat(reloadedAccount.getUserStatusSyncBlockReason()).isNull();

        List<AuthSession> sessions = authSessionRepository.findAll();
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).getIsActive()).isFalse();
        assertThat(sessions.get(0).getExpiresAt()).isBeforeOrEqualTo(LocalDateTime.now());

        verify(restTemplate).exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }
}
