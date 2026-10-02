package com.delivery.auth_service.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.delivery.auth_service.service.FirebaseChatTokenIssuer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;

/**
 * Optional Firebase bridge used only to mint short-lived custom Auth tokens.
 * Firestore data access remains client-side and is protected by deployed Rules.
 */
@Configuration
public class FirebaseChatConfig {

    private final ResourceLoader resourceLoader;

    public FirebaseChatConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Bean
    @ConditionalOnProperty(name = "app.firebase.chat.enabled", havingValue = "true")
    public FirebaseApp firebaseChatApp(
            @Value("${app.firebase.service-account-key-path:}") String serviceAccountKeyPath) throws IOException {
        if (serviceAccountKeyPath == null || serviceAccountKeyPath.isBlank()) {
            throw new IOException("Firebase Chat is enabled but service-account path is empty");
        }

        Resource resource = resourceLoader.getResource(serviceAccountKeyPath);
        if (!resource.exists() || !resource.isReadable()) {
            throw new IOException("Firebase service account is not readable: " + serviceAccountKeyPath);
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        try (InputStream serviceAccount = resource.getInputStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            return FirebaseApp.initializeApp(options);
        }
    }

    @Bean
    @ConditionalOnProperty(name = "app.firebase.chat.enabled", havingValue = "true")
    public FirebaseAuth firebaseChatAuth(FirebaseApp firebaseChatApp) {
        return FirebaseAuth.getInstance(firebaseChatApp);
    }

    @Bean
    @ConditionalOnProperty(name = "app.firebase.chat.enabled", havingValue = "true")
    public FirebaseChatTokenIssuer firebaseChatTokenIssuer(FirebaseAuth firebaseChatAuth) {
        return firebaseChatAuth::createCustomToken;
    }
}
