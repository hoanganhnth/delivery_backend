package com.delivery.auth_service.repository;

import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.entity.AuthSession;
import com.delivery.auth_service.config.UserServiceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
@ImportAutoConfiguration(RestTemplateAutoConfiguration.class)
@Import(UserServiceConfig.class)
@ActiveProfiles("test")
class AuthSessionRepositoryBulkUpdateTest {

    @Autowired
    private AuthAccountRepository accountRepository;

    @Autowired
    private AuthSessionRepository sessionRepository;

    @Test
    void deactivatesByDeviceThenDeactivatesAllWithoutLoadingSessionLists() {
        AuthAccount account = new AuthAccount();
        account.setEmail("user@example.com");
        account.setPasswordHash("hash");
        account.setRole(AuthAccount.Role.USER);
        account.setIsActive(true);
        account = accountRepository.saveAndFlush(account);

        sessionRepository.save(session(account, "phone", "family-1"));
        sessionRepository.save(session(account, "phone", "family-2"));
        sessionRepository.save(session(account, "web", "family-3"));
        sessionRepository.flush();

        LocalDateTime deviceRevokedAt = LocalDateTime.now();
        assertThat(sessionRepository.deactivateActiveSessionsForDevice(
                account.getId(), "phone", deviceRevokedAt)).isEqualTo(2);

        var afterDeviceRevoke = sessionRepository.findAll();
        assertThat(afterDeviceRevoke)
                .filteredOn(session -> "phone".equals(session.getDeviceId()))
                .allSatisfy(session -> {
                    assertThat(session.getIsActive()).isFalse();
                    assertThat(session.getExpiresAt()).isEqualTo(deviceRevokedAt);
                });
        assertThat(afterDeviceRevoke)
                .filteredOn(session -> "web".equals(session.getDeviceId()))
                .allSatisfy(session -> assertThat(session.getIsActive()).isTrue());

        LocalDateTime accountRevokedAt = deviceRevokedAt.plusSeconds(1);
        assertThat(sessionRepository.deactivateAllActiveSessions(
                account.getId(), accountRevokedAt)).isEqualTo(1);
        assertThat(sessionRepository.findAll()).allSatisfy(session ->
                assertThat(session.getIsActive()).isFalse());
    }

    private AuthSession session(AuthAccount account, String deviceId, String tokenFamilyId) {
        AuthSession session = new AuthSession();
        session.setAuthAccount(account);
        session.setDeviceId(deviceId);
        session.setTokenFamilyId(tokenFamilyId);
        session.setIsActive(true);
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        return session;
    }
}
