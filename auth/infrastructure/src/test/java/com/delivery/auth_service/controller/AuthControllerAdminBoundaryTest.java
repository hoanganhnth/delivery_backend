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
