package com.delivery.user_service.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/** Verifies the Auth-signed handoff locally; registration never calls Auth. */
@Component
public class ProvisioningTokenVerifier {
    private final JwtDecoder decoder;

    public ProvisioningTokenVerifier(
            @Value("${app.auth.jwks-uri:${AUTH_JWKS_URI:http://auth-service:8081/.well-known/jwks.json}}") String jwksUri,
            @Value("${app.jwt.issuer:${JWT_ISSUER:delivery-auth}}") String issuer,
            @Value("${app.jwt.provisioning-audience:${JWT_PROVISIONING_AUDIENCE:delivery-user-registration}}") String audience) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new ClaimValidator("token_type", "provisioning"),
                new AudienceValidator(audience)));
        this.decoder = jwtDecoder;
    }

    public ProvisioningIdentity verify(String rawToken) {
        Jwt jwt = decoder.decode(rawToken);
        Long principalId = positiveLong(jwt.getClaim("principal_id"));
        String email = jwt.getClaimAsString("email");
        String role = jwt.getClaimAsString("role");
        if (principalId == null || email == null || email.isBlank() || role == null || role.isBlank()) {
            throw new IllegalArgumentException("Invalid provisioning token identity");
        }
        return new ProvisioningIdentity(principalId, email, role);
    }

    private static Long positiveLong(Object value) {
        if (value instanceof Number number) {
            long parsed = number.longValue();
            return parsed > 0 ? parsed : null;
        }
        if (!(value instanceof String string)) return null;
        try {
            long parsed = Long.parseLong(string);
            return parsed > 0 ? parsed : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public record ProvisioningIdentity(Long principalId, String email, String role) { }

    private record ClaimValidator(String claim, String expected) implements OAuth2TokenValidator<Jwt> {
        @Override public OAuth2TokenValidatorResult validate(Jwt jwt) {
            return expected.equals(jwt.getClaimAsString(claim))
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid " + claim, null));
        }
    }

    private record AudienceValidator(String expected) implements OAuth2TokenValidator<Jwt> {
        @Override public OAuth2TokenValidatorResult validate(Jwt jwt) {
            List<String> audiences = jwt.getAudience();
            return audiences != null && audiences.contains(expected)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        }
    }
}
