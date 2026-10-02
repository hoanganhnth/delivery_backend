package com.delivery.auth_service.service;

import org.junit.jupiter.api.Test;

import com.delivery.auth_service.exception.InvalidCredentialsException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleTokenVerifierTest {

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
