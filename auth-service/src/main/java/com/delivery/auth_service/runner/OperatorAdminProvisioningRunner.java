package com.delivery.auth_service.runner;

import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth_service.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * One-shot local/operator fixture path for ADMIN accounts. It is enabled only
 * by explicit environment/config and creates the Auth + User projection through
 * AuthService, not by public self-registration or SQL patching.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OperatorAdminProvisioningRunner implements ApplicationRunner {

    private final AuthService authService;
    private final ConfigurableApplicationContext applicationContext;

    @Value("${app.operator.admin-provisioning.enabled:false}")
    private boolean enabled;

    @Value("${app.operator.admin-provisioning.email:}")
    private String email;

    @Value("${app.operator.admin-provisioning.password:}")
    private String password;

    @Value("${app.operator.admin-provisioning.exit-after-run:false}")
    private boolean exitAfterRun;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        AuthAccount account = authService.operatorProvisionAdminAccount(email, password);
        log.info("Operator-provisioned ADMIN authAccountId={}, userId={}, email={}",
                account.getId(), account.getUserId(), account.getEmail());
        if (exitAfterRun) {
            int exitCode = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(exitCode);
        }
    }
}
