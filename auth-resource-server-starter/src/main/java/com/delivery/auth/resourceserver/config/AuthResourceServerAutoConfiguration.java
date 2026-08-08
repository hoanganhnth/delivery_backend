package com.delivery.auth.resourceserver.config;

import com.delivery.auth.resourceserver.security.DeliveryJwtAuthenticationConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.List;

@AutoConfiguration
public class AuthResourceServerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AuthResourceServerAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public DeliveryJwtAuthenticationConverter deliveryJwtAuthenticationConverter() {
        return new DeliveryJwtAuthenticationConverter();
    }

    // The SecurityFilterChain is intentionally left out of auto-configuration
    // so that each microservice explicitly declares its own security policy
    // for public, anonymous, and bearer token routes.

    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(
            @Value("${app.auth.jwks-uri:${AUTH_JWKS_URI:http://auth-service:8081/.well-known/jwks.json}}") String jwksUri,
            @Value("${app.jwt.issuer:${JWT_ISSUER:delivery-auth}}") String issuer,
            @Value("${app.jwt.audience:${JWT_AUDIENCE:delivery-api}}") String expectedAudience) {

        log.info("Configuring JWKS JwtDecoder with jwksUri={}, issuer={}, audience={}", jwksUri, issuer, expectedAudience);
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> strictValidator = new DelegatingOAuth2TokenValidator<>(
                withIssuer,
                new AudienceValidator(expectedAudience),
                new TokenTypeValidator("access"),
                new KidValidator(),
                new AlgorithmValidator(SignatureAlgorithm.RS256.getName())
        );

        jwtDecoder.setJwtValidator(strictValidator);
        return jwtDecoder;
    }

    public static class AudienceValidator implements OAuth2TokenValidator<Jwt> {
        private final String expectedAudience;

        public AudienceValidator(String expectedAudience) {
            this.expectedAudience = expectedAudience;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            List<String> audience = jwt.getAudience();
            if (audience != null && audience.contains(expectedAudience)) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error("invalid_token", "The required audience '" + expectedAudience + "' is missing", null);
            return OAuth2TokenValidatorResult.failure(error);
        }
    }

    public static class TokenTypeValidator implements OAuth2TokenValidator<Jwt> {
        private final String expectedTokenType;

        public TokenTypeValidator(String expectedTokenType) {
            this.expectedTokenType = expectedTokenType;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            String tokenType = jwt.getClaimAsString("token_type");
            if (expectedTokenType.equals(tokenType)) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error("invalid_token", "Invalid or missing token_type claim. Expected '" + expectedTokenType + "'", null);
            return OAuth2TokenValidatorResult.failure(error);
        }
    }

    public static class KidValidator implements OAuth2TokenValidator<Jwt> {
        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            String kid = jwt.getHeaders().get("kid") != null ? jwt.getHeaders().get("kid").toString() : null;
            if (kid != null && !kid.isBlank()) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error("invalid_token", "Missing mandatory 'kid' header parameter", null);
            return OAuth2TokenValidatorResult.failure(error);
        }
    }

    public static class AlgorithmValidator implements OAuth2TokenValidator<Jwt> {
        private final String expectedAlgorithm;

        public AlgorithmValidator(String expectedAlgorithm) {
            this.expectedAlgorithm = expectedAlgorithm;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            String algorithm = jwt.getHeaders().get("alg") != null
                    ? jwt.getHeaders().get("alg").toString()
                    : null;
            if (expectedAlgorithm.equals(algorithm)) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error("invalid_token",
                    "Invalid or missing JWS algorithm. Expected '" + expectedAlgorithm + "'", null);
            return OAuth2TokenValidatorResult.failure(error);
        }
    }
}
