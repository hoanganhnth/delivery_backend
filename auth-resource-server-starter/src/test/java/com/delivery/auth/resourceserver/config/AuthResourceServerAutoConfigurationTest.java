package com.delivery.auth.resourceserver.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthResourceServerAutoConfigurationTest {

    private final OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer("delivery-auth"),
            new AuthResourceServerAutoConfiguration.AudienceValidator("delivery-api"),
            new AuthResourceServerAutoConfiguration.TokenTypeValidator("access"),
            new AuthResourceServerAutoConfiguration.KidValidator(),
            new AuthResourceServerAutoConfiguration.AlgorithmValidator("RS256"));

    @Test
    void acceptsOnlyRs256AccessTokensWithIssuerAudienceAndKid() {
        assertThat(validator.validate(jwt("RS256", "auth-key-current", "delivery-auth",
                List.of("delivery-api"), "access")).hasErrors()).isFalse();
    }

    @Test
    void rejectsWrongIssuerAudienceRefreshOrMissingKid() {
        assertRejected(jwt("RS256", "auth-key-current", "other-issuer",
                List.of("delivery-api"), "access"));
        assertRejected(jwt("RS256", "auth-key-current", "delivery-auth",
                List.of("other-audience"), "access"));
        assertRejected(jwt("RS256", "auth-key-current", "delivery-auth",
                List.of("delivery-api"), "refresh"));
        assertRejected(jwt("RS256", null, "delivery-auth", List.of("delivery-api"), "access"));
    }

    @Test
    void rejectsAValidLookingTokenWhenItsAlgorithmIsNotRs256() {
        assertRejected(jwt("RS384", "auth-key-current", "delivery-auth",
                List.of("delivery-api"), "access"));
    }

    @Test
    void acceptsEitherPublishedKidDuringKeyRotation() {
        assertThat(validator.validate(jwt("RS256", "auth-key-current", "delivery-auth",
                List.of("delivery-api"), "access")).hasErrors()).isFalse();
        assertThat(validator.validate(jwt("RS256", "auth-key-retiring", "delivery-auth",
                List.of("delivery-api"), "access")).hasErrors()).isFalse();
    }

    private void assertRejected(Jwt jwt) {
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    private Jwt jwt(String algorithm, String kid, String issuer, List<String> audience, String tokenType) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("access-token")
                .header("alg", algorithm)
                .subject("7")
                .issuer(issuer)
                .audience(audience)
                .claim("token_type", tokenType)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300));
        if (kid != null) {
            builder.header("kid", kid);
        }
        return builder.build();
    }
}
