package com.delivery.notification_service.config;

import com.google.firebase.FirebaseApp;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FirebaseConfig.class);

    @Test
    void doesNotCreateFirebaseBeanWhenCredentialPathIsAbsent() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(FirebaseApp.class));
    }

    @Test
    void failsFastWhenConfiguredCredentialIsUnreadable() {
        contextRunner
                .withPropertyValues("firebase.service-account-key-path=file:/missing/firebase.json")
                .run(context -> assertThat(context).hasFailed());
    }
}
