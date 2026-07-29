package com.delivery.auth_service.runner;

import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OperatorAdminProvisioningRunnerTest {

    private final AuthService authService = mock(AuthService.class);
    private final ConfigurableApplicationContext applicationContext =
            mock(ConfigurableApplicationContext.class);
    private final ApplicationArguments args = mock(ApplicationArguments.class);
    private final OperatorAdminProvisioningRunner runner =
            new OperatorAdminProvisioningRunner(authService, applicationContext);

    @Test
    void disabledRunnerDoesNotProvisionAdmin() {
        ReflectionTestUtils.setField(runner, "enabled", false);

        runner.run(args);

        verifyNoInteractions(authService);
    }

    @Test
    void enabledRunnerProvisionsAdminWithExplicitCredentials() {
        ReflectionTestUtils.setField(runner, "enabled", true);
        ReflectionTestUtils.setField(runner, "email", "admin@example.com");
        ReflectionTestUtils.setField(runner, "password", "secret");
        ReflectionTestUtils.setField(runner, "exitAfterRun", false);

        AuthAccount account = new AuthAccount();
        ReflectionTestUtils.setField(account, "id", 7L);
        account.setUserId(17L);
        account.setEmail("admin@example.com");
        account.setRole(AuthAccount.Role.ADMIN);
        when(authService.operatorProvisionAdminAccount("admin@example.com", "secret"))
                .thenReturn(account);

        runner.run(args);

        verify(authService).operatorProvisionAdminAccount("admin@example.com", "secret");
    }
}
