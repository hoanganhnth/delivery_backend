package com.delivery.auth_service.controller;

import com.delivery.auth_service.dto.AuthAccountDto;
import com.delivery.auth_service.dto.BlockAccountRequest;
import com.delivery.auth_service.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerAdminBoundaryTest {

    private final AuthService authService = mock(AuthService.class);
    private final AuthController controller = new AuthController(authService);

    @Test
    void accountReadRequiresAdminRoleBeforeServiceCall() {
        var forbidden = controller.getAccountById(7L, "USER");

        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        verify(authService, never()).getAccountByIdDto(7L);
    }

    @Test
    void accountReadAcceptsAdminRole() {
        AuthAccountDto account = new AuthAccountDto(7L, "admin@example.com", "ADMIN");
        when(authService.getAccountByIdDto(7L)).thenReturn(account);

        var response = controller.getAccountById(7L, "ADMIN");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo(account);
        verify(authService).getAccountByIdDto(7L);
    }

    @Test
    void sessionsFailClosedWithoutAuthentication() {
        SecurityContextHolder.clearContext();

        var response = controller.getSessions();

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        verify(authService, never()).getActiveSessions(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void blockAccountChecksRoleBeforePayloadAndBoundsReason() {
        BlockAccountRequest tooLong = new BlockAccountRequest("x".repeat(501));

        var forbidden = controller.blockAccount(7L, "USER", 1L, tooLong);
        var invalid = controller.blockAccount(7L, "ADMIN", 1L, tooLong);

        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        verify(authService, never()).blockAccount(7L, 1L, tooLong.getReason());
    }

    @Test
    void adminIdentityIsRequiredForStatusMutations() {
        var block = controller.blockAccount(7L, "ADMIN", null, null);
        var unblock = controller.unblockAccount(7L, "ADMIN", null);

        assertThat(block.getStatusCode().value()).isEqualTo(400);
        assertThat(unblock.getStatusCode().value()).isEqualTo(400);
        verify(authService, never()).blockAccount(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(authService, never()).unblockAccount(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }
}
