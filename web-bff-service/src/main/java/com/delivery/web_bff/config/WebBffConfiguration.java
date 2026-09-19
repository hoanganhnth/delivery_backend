package com.delivery.web_bff.config;

import com.delivery.web_bff.session.SessionFactory;
import com.delivery.web_bff.session.TokenVault;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class WebBffConfiguration {
    @Bean
    TokenVault tokenVault(@Value("${web-bff.encryption-key-version}") String version,
            @Value("${web-bff.encryption-key-base64}") String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException("WEB_BFF_ENCRYPTION_KEY_BASE64 is required");
        }
        return new TokenVault(version, Base64.getDecoder().decode(encodedKey), new SecureRandom());
    }

    @Bean
    SessionFactory sessionFactory(TokenVault vault,
            @Value("${web-bff.session-max-age-seconds}") long maxAgeSeconds) {
        return new SessionFactory(vault, new SecureRandom(), Duration.ofSeconds(maxAgeSeconds));
    }

    @Bean
    RestClient authRestClient(@Value("${web-bff.gateway-base-url}") String gatewayBaseUrl) {
        return RestClient.builder().baseUrl(gatewayBaseUrl).build();
    }

    @Bean
    OncePerRequestFilter webBffOriginFilter(
            @Value("${web-bff.allowed-origins}") String allowedOrigins) {
        return new WebBffOriginFilter(java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(origin -> !origin.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }
}
