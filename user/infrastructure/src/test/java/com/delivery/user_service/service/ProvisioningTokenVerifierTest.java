package com.delivery.user_service.service;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProvisioningTokenVerifierTest {
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final ProvisioningTokenVerifier verifier =
            new ProvisioningTokenVerifier("http://localhost/jwks", "delivery-auth", "registration");

    private Jwt identity(Object principal, String email, String role) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("principal_id")).thenReturn(principal);
        when(jwt.getClaimAsString("email")).thenReturn(email);
        when(jwt.getClaimAsString("role")).thenReturn(role);
        ReflectionTestUtils.setField(verifier, "decoder", decoder);
        when(decoder.decode("handoff")).thenReturn(jwt);
        return jwt;
    }

    @Test
    void acceptsNumericAndStringPrincipalIds() {
        for (Object principal : List.of(7L, "7")) {
            identity(principal, "user@example.com", "USER");
            assertThat(verifier.verify("handoff"))
                    .isEqualTo(new com.delivery.user.application.api.ProvisioningIdentity(7L, "user@example.com", "USER"));
        }
    }

    static Stream<Object> invalidPrincipals() {
        return Stream.of(null, 0L, -1L, "0", "-1", "", "abc", "9223372036854775808", Boolean.TRUE);
    }

    @ParameterizedTest
    @MethodSource("invalidPrincipals")
    void rejectsInvalidPrincipal(Object principal) {
        identity(principal, "user@example.com", "USER");
        assertThatThrownBy(() -> verifier.verify("handoff"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid provisioning token identity");
    }

    @Test
    void rejectsMissingOrBlankEmailAndRole() {
        for (String missing : new String[] {null, "", " "}) {
            identity(7L, missing, "USER");
            assertThatThrownBy(() -> verifier.verify("handoff")).hasMessage("Invalid provisioning token identity");
            identity(7L, "user@example.com", missing);
            assertThatThrownBy(() -> verifier.verify("handoff")).hasMessage("Invalid provisioning token identity");
        }
    }

    @Test
    void propagatesDecoderRejection() {
        ReflectionTestUtils.setField(verifier, "decoder", decoder);
        when(decoder.decode("invalid")).thenThrow(new JwtException("signature rejected"));
        assertThatThrownBy(() -> verifier.verify("invalid"))
                .isInstanceOf(JwtException.class).hasMessage("signature rejected");
    }

    @SuppressWarnings("unchecked")
    private OAuth2TokenValidator<Jwt> validator(String name, String[] arguments) throws Exception {
        Class<?> type = Class.forName(ProvisioningTokenVerifier.class.getName() + "$" + name);
        Class<?>[] types = java.util.Arrays.stream(arguments).map(value -> String.class).toArray(Class<?>[]::new);
        var constructor = type.getDeclaredConstructor(types);
        constructor.setAccessible(true);
        return (OAuth2TokenValidator<Jwt>) constructor.newInstance((Object[]) arguments);
    }

    @Test
    void audienceMustContainTheRegistrationAudience() throws Exception {
        var validator = validator("AudienceValidator", new String[] {"registration"});
        Jwt jwt = mock(Jwt.class);
        when(jwt.getAudience()).thenReturn(null, List.of(), List.of("access"), List.of("access", "registration"));
        for (int attempt = 0; attempt < 3; attempt++) {
            var result = validator.validate(jwt);
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.getErrors()).extracting(error -> error.getDescription()).containsExactly("Invalid audience");
        }
        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void tokenTypeMustBeProvisioning() throws Exception {
        var validator = validator("ClaimValidator", new String[] {"token_type", "provisioning"});
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("token_type")).thenReturn(null, "access", "provisioning");
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThat(validator.validate(jwt).getErrors()).extracting(error -> error.getDescription())
                    .containsExactly("Invalid token_type");
        }
        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }
}
