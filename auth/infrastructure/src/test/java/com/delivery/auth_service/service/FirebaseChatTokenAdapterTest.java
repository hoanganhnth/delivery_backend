package com.delivery.auth_service.service;

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

class FirebaseChatTokenAdapterTest {

    @Test
    void refusesToMintWhenFeatureIsDisabled() {
        FirebaseChatTokenIssuer firebaseAuth = mock(FirebaseChatTokenIssuer.class);
        com.delivery.auth.application.DefaultFirebaseChatTokenUseCase service = new com.delivery.auth.application.DefaultFirebaseChatTokenUseCase(new FirebaseChatTokenAdapter(
                providerOf(firebaseAuth)), false);

        assertThatThrownBy(() -> service.issue(actor("USER")))
                .isInstanceOf(com.delivery.auth.domain.policy.FirebaseChatUnavailable.class)
                .hasMessage("Firebase support chat is disabled");
    }

    @Test
    void mintsTokenWithCanonicalPrincipalAndRoleClaims() throws Exception {
        FirebaseChatTokenIssuer firebaseAuth = mock(FirebaseChatTokenIssuer.class);
        when(firebaseAuth.createCustomToken(eq("42"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn("custom-token");
        com.delivery.auth.application.DefaultFirebaseChatTokenUseCase service = new com.delivery.auth.application.DefaultFirebaseChatTokenUseCase(new FirebaseChatTokenAdapter(
                providerOf(firebaseAuth)), true);

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
        com.delivery.auth.application.DefaultFirebaseChatTokenUseCase service = new com.delivery.auth.application.DefaultFirebaseChatTokenUseCase(new FirebaseChatTokenAdapter(
                providerOf(firebaseAuth)), true);

        assertThatThrownBy(() -> service.issue(new com.delivery.auth.application.api.FirebaseChatTokenUseCase.Actor(null, java.util.Set.of("USER"), false)))
                .isInstanceOf(com.delivery.auth.domain.policy.FirebaseChatUnavailable.class);
    }

    @Test
    void sdkFailureKeepsTheUnavailableContractAndCause() throws Exception {
        FirebaseChatTokenIssuer issuer = mock(FirebaseChatTokenIssuer.class);
        var failure = mock(com.google.firebase.auth.FirebaseAuthException.class);
        when(issuer.createCustomToken(eq("42"), org.mockito.ArgumentMatchers.anyMap())).thenThrow(failure);
        var core = new com.delivery.auth.application.DefaultFirebaseChatTokenUseCase(
                new FirebaseChatTokenAdapter(providerOf(issuer)), true);
        assertThatThrownBy(() -> core.issue(actor("USER")))
                .isInstanceOf(com.delivery.auth.domain.policy.FirebaseChatUnavailable.class)
                .hasMessage("Firebase custom token could not be created").hasCause(failure);
        assertThat(new FirebaseChatTokenAdapter(providerOf(null)).available()).isFalse();
    }

    private static com.delivery.auth.application.api.FirebaseChatTokenUseCase.Actor actor(String role) {
        return new com.delivery.auth.application.api.FirebaseChatTokenUseCase.Actor(42L, java.util.Set.of(role), "ADMIN".equals(role));
    }

    private static ObjectProvider<FirebaseChatTokenIssuer> providerOf(FirebaseChatTokenIssuer value) {
        return new ObjectProvider<>() {
            @Override
            public FirebaseChatTokenIssuer getObject() { return value; }

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
