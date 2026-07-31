package com.delivery.auth_service.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;

import com.delivery.auth_service.TestJwtKeyProperties;
import com.delivery.auth_service.config.AuthUserCircuitBreaker;
import com.delivery.auth_service.config.UserServiceConfig;
import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.repository.AuthAccountRepository;
import com.delivery.auth_service.repository.AuthSessionRepository;
import com.delivery.auth_service.repository.RefreshTokenRecordRepository;

import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_block_tx;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.kafka.listener.auto-startup=false"
})
@ActiveProfiles("test")
class AuthServiceBlockAccountTransactionTest {

    @DynamicPropertySource
    static void jwtKeyProperties(DynamicPropertyRegistry registry) {
        TestJwtKeyProperties.register(registry);
    }

    @Autowired
    private AuthService authService;

    @MockBean
    private AuthAccountRepository authAccountRepository;

    @MockBean
    private AuthSessionRepository authSessionRepository;

    @MockBean
    private RefreshTokenRecordRepository refreshTokenRecordRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private TokenService tokenService;

    @MockBean
    private UserServiceConfig userServiceConfig;

    @MockBean
    private AuthUserCircuitBreaker authUserCircuitBreaker;

    @MockBean
    private RestTemplate restTemplate;

    @MockBean
    private GoogleTokenVerifier googleTokenVerifier;

    @Test
    void blockAccountExecutesBulkSessionUpdateInsideActiveTransaction() {
        AuthAccount account = new AuthAccount();
        ReflectionTestUtils.setField(account, "id", 3L);
        account.setIsActive(true);
        account.setUserId(null);

        when(authAccountRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(account));
        when(authAccountRepository.save(any(AuthAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(authSessionRepository.deactivateAllActiveSessions(eq(3L), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                    return 1;
                });

        authService.blockAccount(3L, 1L, "fraud review");

        verify(authSessionRepository).deactivateAllActiveSessions(eq(3L), any(LocalDateTime.class));
    }
}
