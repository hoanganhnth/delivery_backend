package com.delivery.auth_service.runner;

import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth.application.api.OperatorProvisioningUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OperatorAdminProvisioningRunnerTest {

    private final OperatorProvisioningUseCase authService = mock(OperatorProvisioningUseCase.class);
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

        AuthAccount account = new AuthAccount(7L,17L,AuthAccount.LifecycleStatus.ACTIVE,1L,
                "admin@example.com","hash",AuthAccount.Role.ADMIN,true,false,null,false,0L,null,null,0,null,
                false,null,null,0L,null,null,null);
        when(authService.provisionAdmin("admin@example.com", "secret"))
                .thenReturn(account);

        runner.run(args);

        verify(authService).provisionAdmin("admin@example.com", "secret");
    }
}
