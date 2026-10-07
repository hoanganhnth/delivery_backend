package com.delivery.auth_service.controller;

import com.delivery.auth_service.dto.AuthAccountDto;
import com.delivery.auth_service.dto.BlockAccountRequest;
import com.delivery.auth_service.entity.AuthAccount;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.auth.resourceserver.security.AuthenticatedActorAuthenticationToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerAdminBoundaryTest {

    private final com.delivery.auth.application.api.SecurityTokenUseCase security = mock(com.delivery.auth.application.api.SecurityTokenUseCase.class);
    private final com.delivery.auth.application.api.AccountLifecycleUseCase lifecycle = mock(com.delivery.auth.application.api.AccountLifecycleUseCase.class);
    private final com.delivery.auth.application.api.FirebaseChatTokenUseCase firebase = mock(com.delivery.auth.application.api.FirebaseChatTokenUseCase.class);
    private final com.delivery.auth.application.api.DeviceSessionUseCase deviceSessions = mock(com.delivery.auth.application.api.DeviceSessionUseCase.class);
    private final com.delivery.auth.application.api.AccountLookupUseCase accounts = mock(com.delivery.auth.application.api.AccountLookupUseCase.class);
    private final AuthController controller = new AuthController(security, lifecycle, firebase, mock(com.delivery.auth.application.api.RegistrationUseCase.class), mock(com.delivery.auth.application.api.LoginUseCase.class), mock(com.delivery.auth.application.api.RefreshTokenUseCase.class), mock(com.delivery.auth.application.api.LogoutUseCase.class), deviceSessions, mock(com.delivery.auth.application.api.RegistrationRecoveryUseCase.class), mock(com.delivery.auth.application.api.RegistrationAdmissionUseCase.class), mock(com.delivery.auth.application.api.SocialLoginUseCase.class), accounts);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void fallbackAdminAuthoritiesAndIdentityResolveWithoutTrustingRequestAdminId() {
        for (String authority : List.of("ADMIN", "ROLE_ADMIN")) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "42", "unused", List.of(new SimpleGrantedAuthority(authority))));
            assertThat(controller.blockAccount(7L, null).getStatusCode().value()).isEqualTo(200);
            assertThat(controller.unblockAccount(7L).getStatusCode().value()).isEqualTo(200);
        }
        verify(lifecycle, org.mockito.Mockito.times(2)).block(7L, 42L, "Blocked by admin");
        verify(lifecycle, org.mockito.Mockito.times(2)).unblock(7L, 42L);
    }

    @Test
    void adminWithoutResolvableIdentityCannotMutateAccount() {
        for (String name : List.of("", " ", "unknown@example.com")) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    name, "unused", List.of(new SimpleGrantedAuthority("ADMIN"))));
            assertThat(controller.blockAccount(7L, null).getBody().getMessage()).isEqualTo("Admin ID is required");
            assertThat(controller.unblockAccount(7L).getStatusCode().value()).isEqualTo(400);
        }
        org.mockito.Mockito.verifyNoInteractions(lifecycle);
    }

    @Test
    void actorEmailFallbackAndReasonBoundaryReachLifecycle() {
        var actor = new AuthenticatedActor(null, "admin@example.com", Set.of("ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, "unused"));
        when(accounts.byEmail("admin@example.com")).thenReturn(Optional.of(
                new com.delivery.auth.application.api.AccountSnapshot(42L, 70L, "admin@example.com",
                        com.delivery.auth.domain.model.AuthAccount.Role.ADMIN,
                        com.delivery.auth.domain.model.AuthAccount.LifecycleStatus.ACTIVE, true, false, null)));
        assertThat(controller.blockAccount(7L, new BlockAccountRequest(null)).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.blockAccount(7L, new BlockAccountRequest("x".repeat(500))).getStatusCode().value()).isEqualTo(200);
        verify(lifecycle).block(7L, 42L, "Blocked by admin");
        verify(lifecycle).block(7L, 42L, "x".repeat(500));
    }

    @Test
    void sessionRevocationRejectsAbsentAndBlankAuthentication() {
        assertThat(controller.revokeDeviceSession("device").getStatusCode().value()).isEqualTo(401);
        for (String name : List.of("", " ")) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(name, "unused"));
            assertThat(controller.getSessions().getStatusCode().value()).isEqualTo(401);
            assertThat(controller.revokeDeviceSession("device").getBody().getMessage()).isEqualTo("Unauthorized");
        }
        org.mockito.Mockito.verifyNoInteractions(deviceSessions);
    }

    @Test
    void sessionMappingPreservesNullableDeviceTypeAndRevocationOwner() {
        setSecurityContext("user@example.com", "ROLE_USER", 10L);
        when(deviceSessions.activeSessions("user@example.com")).thenReturn(List.of(
                new com.delivery.auth.domain.model.Session(1L, 10L, "unknown", "old device", null, "ip", "family", true, null, null, null),
                new com.delivery.auth.domain.model.Session(2L, 10L, "web", "browser", com.delivery.auth.domain.model.Session.DeviceType.WEB, "ip", "family", true, null, null, null)));
        var response = controller.getSessions();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getData()).extracting(com.delivery.auth_service.dto.SessionInfoResponse::getDeviceType)
                .containsExactly(null, "web");
        assertThat(controller.revokeDeviceSession("web").getStatusCode().value()).isEqualTo(200);
        verify(deviceSessions).revokeDevice("user@example.com", "web");
    }

    @Test
    void accountReadRequiresAdminRoleBeforeServiceCall() {
        setSecurityContext("user@example.com", "ROLE_USER", 10L);

        var forbidden = controller.getAccountById(7L);

        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        verify(accounts, never()).requireById(7L);
    }

    @Test
    void accountReadAcceptsAdminRole() {
        setSecurityContext("admin@example.com", "ROLE_ADMIN", 1L);
        AuthAccountDto account = new AuthAccountDto(7L, "admin@example.com", "ADMIN");
        when(accounts.requireById(7L)).thenReturn(new com.delivery.auth.application.api.AccountSnapshot(
                7L, 70L, "admin@example.com", com.delivery.auth.domain.model.AuthAccount.Role.ADMIN,
                com.delivery.auth.domain.model.AuthAccount.LifecycleStatus.ACTIVE, true, false, null));

        var response = controller.getAccountById(7L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo(account);
        verify(accounts).requireById(7L);
    }

    @Test
    void sessionsFailClosedWithoutAuthentication() {
        SecurityContextHolder.clearContext();

        var response = controller.getSessions();

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        verify(deviceSessions, never()).activeSessions(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void blockAccountChecksRoleBeforePayloadAndBoundsReason() {
        BlockAccountRequest tooLong = new BlockAccountRequest("x".repeat(501));

        setSecurityContext("user@example.com", "ROLE_USER", 10L);
        var forbidden = controller.blockAccount(7L, tooLong);

        setSecurityContext("admin@example.com", "ROLE_ADMIN", 1L);
        var invalid = controller.blockAccount(7L, tooLong);

        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        verify(lifecycle, never()).block(7L, 1L, tooLong.getReason());
    }

    @Test
    void adminIdentityIsRequiredForStatusMutations() {
        SecurityContextHolder.clearContext();

        var block = controller.blockAccount(7L, null);
        var unblock = controller.unblockAccount(7L);

        assertThat(block.getStatusCode().value()).isEqualTo(403);
        assertThat(unblock.getStatusCode().value()).isEqualTo(403);
        verify(lifecycle, never()).block(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(lifecycle, never()).unblock(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    private void setSecurityContext(String email, String role, Long userId) {
        AuthenticatedActor actor = new AuthenticatedActor(userId, email, Set.of(role.replace("ROLE_", "")));
        var token = new AuthenticatedActorAuthenticationToken(
                null,
                actor,
                List.of(new SimpleGrantedAuthority(role))
        );
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
