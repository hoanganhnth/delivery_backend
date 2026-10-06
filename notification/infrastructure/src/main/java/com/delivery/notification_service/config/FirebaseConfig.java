package com.delivery.notification_service.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;

/**
 * ✅ Firebase Configuration cho Push Notifications theo Backend Instructions
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    private final ResourceLoader resourceLoader;

    public FirebaseConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Bean
    @ConditionalOnProperty(name = "firebase.service-account-key-path")
    public FirebaseApp firebaseApp(
            @Value("${firebase.service-account-key-path}") String serviceAccountKeyPath) throws IOException {
        Resource resource = resourceLoader.getResource(serviceAccountKeyPath);
        if (!resource.exists() || !resource.isReadable()) {
            throw new IOException("Firebase service account is not readable: " + serviceAccountKeyPath);
        }

        try {
            // Check if FirebaseApp is already initialized
            if (!FirebaseApp.getApps().isEmpty()) {
                return FirebaseApp.getInstance();
            }

            GoogleCredentials credentials;
            try (InputStream serviceAccount = resource.getInputStream()) {
                credentials = GoogleCredentials.fromStream(serviceAccount);
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();

            FirebaseApp app = FirebaseApp.initializeApp(options);
            log.info("✅ Firebase initialized successfully");
            return app;

        } catch (IOException e) {
            log.error("💥 Failed to initialize Firebase: {}", e.getMessage());
            throw e;
        }
    }
}
