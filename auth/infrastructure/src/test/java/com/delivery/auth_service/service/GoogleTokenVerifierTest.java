package com.delivery.auth_service.service;

import org.junit.jupiter.api.Test;

import com.delivery.auth_service.exception.InvalidCredentialsException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;

class GoogleTokenVerifierTest {

    @Test
    void verifiesConfiguredAudiencesAndRejectsUnverifiedOrMissingEmail() throws Exception {
        GoogleIdTokenVerifier delegate = mock(GoogleIdTokenVerifier.class);
        try (var builders = mockConstruction(GoogleIdTokenVerifier.Builder.class, (builder, context) -> {
            when(builder.setAudience(any())).thenReturn(builder);
            when(builder.build()).thenReturn(delegate);
        })) {
            var verifier = new GoogleTokenVerifier(" client-one , , client-two ");
            GoogleIdToken token = mock(GoogleIdToken.class);
            when(delegate.verify("signed-token")).thenReturn(token);
            for (Boolean verified : new Boolean[] {null, false, true}) {
                for (String email : new String[] {null, "", " ", "user@example.com"}) {
                    var payload = new GoogleIdToken.Payload().setEmailVerified(verified).setEmail(email);
                    when(token.getPayload()).thenReturn(payload);
                    if (Boolean.TRUE.equals(verified) && "user@example.com".equals(email)) {
                        assertThat(verifier.verify("signed-token")).isSameAs(payload);
                    } else {
                        assertThatThrownBy(() -> verifier.verify("signed-token"))
                                .isInstanceOf(InvalidCredentialsException.class)
                                .hasMessage("Google account email is not verified");
                    }
                }
            }
            for (var builder : builders.constructed()) {
                verify(builder).setAudience(java.util.List.of("client-one", "client-two"));
            }
        }
    }

    @Test
    void rejectsMissingTokenOrPayloadAndMasksProviderFailure() throws Exception {
        GoogleIdTokenVerifier delegate = mock(GoogleIdTokenVerifier.class);
        try (var builders = mockConstruction(GoogleIdTokenVerifier.Builder.class, (builder, context) -> {
            when(builder.setAudience(any())).thenReturn(builder);
            when(builder.build()).thenReturn(delegate);
        })) {
            var verifier = new GoogleTokenVerifier("client");
            GoogleIdToken token = mock(GoogleIdToken.class);
            when(delegate.verify("signed-token")).thenReturn(null, token)
                    .thenThrow(new java.io.IOException("provider details"));
            for (int attempt = 0; attempt < 3; attempt++) {
                assertThatThrownBy(() -> verifier.verify("signed-token"))
                        .isInstanceOf(InvalidCredentialsException.class).hasMessage("Invalid Google ID token");
            }
        }
    }

    @Test
    void failsClosedWhenGoogleClientIdsAreNotConfigured() {
        GoogleTokenVerifier verifier = new GoogleTokenVerifier("");

        assertThatThrownBy(() -> verifier.verify("untrusted-token"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void neverAcceptsMalformedTokenAsParsedIdentity() {
        GoogleTokenVerifier verifier = new GoogleTokenVerifier("client-id.apps.googleusercontent.com");

        assertThatThrownBy(() -> verifier.verify("not-a-signed-google-token"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid Google ID token");
    }
}
