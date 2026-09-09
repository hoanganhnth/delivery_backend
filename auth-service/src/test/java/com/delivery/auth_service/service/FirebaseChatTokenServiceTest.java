package com.delivery.auth_service.service;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirebaseChatTokenServiceTest {

    @Test
    void refusesToMintWhenFeatureIsDisabled() {
        FirebaseChatTokenIssuer firebaseAuth = mock(FirebaseChatTokenIssuer.class);
        FirebaseChatTokenService service = new FirebaseChatTokenService(
                providerOf(firebaseAuth), false);

        assertThatThrownBy(() -> service.issue(actor("USER")))
                .isInstanceOf(FirebaseChatTokenService.FirebaseChatUnavailableException.class)
                .hasMessage("Firebase support chat is disabled");
    }

    @Test
    void mintsTokenWithCanonicalPrincipalAndRoleClaims() throws Exception {
        FirebaseChatTokenIssuer firebaseAuth = mock(FirebaseChatTokenIssuer.class);
        when(firebaseAuth.createCustomToken(eq("42"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn("custom-token");
        FirebaseChatTokenService service = new FirebaseChatTokenService(
                providerOf(firebaseAuth), true);

        var response = service.issue(actor("ADMIN"));

        assertThat(response.token()).isEqualTo("custom-token");
        assertThat(response.principalId()).isEqualTo(42L);
        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(response.expiresInSeconds()).isEqualTo(3_600L);

        ArgumentCaptor<Map<String, Object>> claims = ArgumentCaptor.forClass(Map.class);
        verify(firebaseAuth).createCustomToken(eq("42"), claims.capture());
        assertThat(claims.getValue()).containsEntry("principalId", "42")
                .containsEntry("role", "ADMIN")
                .containsEntry("supportAgent", true);
    }

    @Test
    void rejectsIdentitiesWithoutAStablePrincipal() {
        FirebaseChatTokenIssuer firebaseAuth = mock(FirebaseChatTokenIssuer.class);
        FirebaseChatTokenService service = new FirebaseChatTokenService(
                providerOf(firebaseAuth), true);

        assertThatThrownBy(() -> service.issue(new AuthenticatedActor(null, null, "x@y.test", java.util.Set.of("USER"))))
                .isInstanceOf(FirebaseChatTokenService.FirebaseChatUnavailableException.class);
    }

    private static AuthenticatedActor actor(String role) {
        return new AuthenticatedActor(42L, 84L, "actor@example.com", java.util.Set.of(role));
    }

    private static ObjectProvider<FirebaseChatTokenIssuer> providerOf(FirebaseChatTokenIssuer value) {
        return new ObjectProvider<>() {
            @Override
            public FirebaseChatTokenIssuer getObject(Object... args) {
                return value;
            }

            @Override
            public FirebaseChatTokenIssuer getIfAvailable() {
                return value;
            }

            @Override
            public FirebaseChatTokenIssuer getIfUnique() {
                return value;
            }
        };
    }
}
