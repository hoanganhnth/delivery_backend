package com.delivery.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RequiredSecretsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RequiredSecretsAutoConfiguration.class);

    @Test
    void failsFastWhenDeploymentMarksInternalCredentialRequiredButItIsBlank() {
        contextRunner
                .withPropertyValues("platform.secrets.internal-secret-required=true", "app.internal.secret=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("INTERNAL_SECRET is required");
                });
    }

    @Test
    void acceptsAnExternallyInjectedInternalCredential() {
        contextRunner
                .withPropertyValues("platform.secrets.internal-secret-required=true", "app.internal.secret=not-logged")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
