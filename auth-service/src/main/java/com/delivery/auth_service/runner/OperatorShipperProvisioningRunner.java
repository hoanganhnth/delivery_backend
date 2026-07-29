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
 * One-shot local/operator fixture path for SHIPPER accounts. It is enabled only
 * by explicit environment/config and creates the Auth + User projection through
 * AuthService, not by public self-registration or SQL patching.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OperatorShipperProvisioningRunner implements ApplicationRunner {

    private final AuthService authService;
    private final ConfigurableApplicationContext applicationContext;

    @Value("${app.operator.shipper-provisioning.enabled:false}")
    private boolean enabled;

    @Value("${app.operator.shipper-provisioning.email:}")
    private String email;

    @Value("${app.operator.shipper-provisioning.password:}")
    private String password;

    @Value("${app.operator.shipper-provisioning.exit-after-run:false}")
    private boolean exitAfterRun;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        AuthAccount account = authService.operatorProvisionShipperAccount(email, password);
        log.info("Operator-provisioned SHIPPER authAccountId={}, userId={}, email={}",
                account.getId(), account.getUserId(), account.getEmail());
        if (exitAfterRun) {
            int exitCode = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(exitCode);
        }
    }
}
