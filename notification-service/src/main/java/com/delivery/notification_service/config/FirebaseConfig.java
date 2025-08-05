package com.delivery.notification_service.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

/**
 * ✅ Firebase Configuration cho Push Notifications theo Backend Instructions
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.service-account-key-path}")
    private String serviceAccountKeyPath;

    @Bean
    public FirebaseApp firebaseApp() {
        try {
            // Check if FirebaseApp is already initialized
            if (!FirebaseApp.getApps().isEmpty()) {
                return FirebaseApp.getInstance();
            }

            // Load service account key
            InputStream serviceAccount;
            try {
                ClassPathResource resource = new ClassPathResource("firebase-service-account.json");
                serviceAccount = resource.getInputStream();
                log.info("✅ Loading Firebase service account from classpath");
            } catch (IOException e) {
                log.warn("⚠️ Firebase service account file not found in classpath, Firebase features will be disabled");
                return null;
            }

            GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccount);

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();

            FirebaseApp app = FirebaseApp.initializeApp(options);
            log.info("✅ Firebase initialized successfully");
            return app;

        } catch (IOException e) {
            log.error("💥 Failed to initialize Firebase: {}", e.getMessage());
            return null;
        }
    }
}
