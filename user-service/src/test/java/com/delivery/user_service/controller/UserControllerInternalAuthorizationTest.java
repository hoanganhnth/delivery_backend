package com.delivery.user_service.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.delivery.user_service.dto.UserRequest;
import com.delivery.user_service.dto.UserResponse;
import com.delivery.user_service.dto.BlockUserRequest;
import com.delivery.user_service.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerInternalAuthorizationTest {

    private final UserService userService = mock(UserService.class);
    private final UserController controller = new UserController(userService);

    @BeforeEach
    void configureSecret() {
        ReflectionTestUtils.setField(controller, "internalSecret", "service-secret");
    }

    @Test
    void createUserFailsClosedWithoutServiceCredential() {
        UserRequest request = new UserRequest();

        var response = controller.createUser(request, null);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(userService, never()).createUser(request);
    }

    @Test
    void createUserAcceptsConfiguredServiceCredential() {
        UserRequest request = new UserRequest();
        UserResponse created = UserResponse.builder().id(7L).build();
        when(userService.createUser(request)).thenReturn(created);

        var response = controller.createUser(request, "service-secret");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(userService).createUser(request);
    }

    @Test
    void authLookupRejectsWrongServiceCredential() {
        var response = controller.getUserByAuthId(9L, "wrong");

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(userService, never()).getUserByAuthId(9L);
    }

    @Test
    void currentProfileUpdateUsesTheGatewayIdentity() {
        UserRequest request = UserRequest.builder().authId(999L).email("ignored@example.com").build();
        UserResponse updated = UserResponse.builder().id(7L).build();
        when(userService.updateUser(7L, request)).thenReturn(updated);

        var response = controller.updateCurrentUser(request, 7L, "USER");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(userService).updateUser(7L, request);
        verify(userService, never()).updateUser(999L, request);
    }

    @Test
    void currentProfileReadFailsClosedWithoutTrustedIdentityHeaders() {
        var missingRole = controller.getUserById(7L, null);
        var missingUserId = controller.updateCurrentUser(new UserRequest(), null, "USER");
        var missingUserRole = controller.updateCurrentUser(new UserRequest(), 7L, null);

        assertThat(missingRole.getStatusCode().value()).isEqualTo(403);
        assertThat(missingUserId.getStatusCode().value()).isEqualTo(403);
        assertThat(missingUserRole.getStatusCode().value()).isEqualTo(403);
        verify(userService, never()).getUserById(7L);
        verify(userService, never()).updateUser(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blockUserChecksRoleBeforePayloadAndBoundsReason() {
        BlockUserRequest tooLong = new BlockUserRequest();
        tooLong.setReason("x".repeat(501));

        var forbidden = controller.blockUser(7L, tooLong, 1L, "USER", "service-secret");
        var invalid = controller.blockUser(7L, tooLong, 1L, "ADMIN", "service-secret");

        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        verify(userService, never()).blockUser(7L, 1L, tooLong.getReason());
    }

    @Test
    void blockAndUnblockRequireTheAuthServiceCredential() {
        BlockUserRequest request = new BlockUserRequest();
        request.setReason("fraud review");

        var blocked = controller.blockUser(7L, request, 1L, "ADMIN", null);
        var unblocked = controller.unblockUser(7L, 1L, "ADMIN", "wrong");

        assertThat(blocked.getStatusCode().value()).isEqualTo(403);
        assertThat(unblocked.getStatusCode().value()).isEqualTo(403);
        verify(userService, never()).blockUser(7L, 1L, request.getReason());
        verify(userService, never()).unblockUser(7L, 1L);
    }

    @Test
    void blockAndUnblockAcceptTheAuthServiceCredential() {
        BlockUserRequest request = new BlockUserRequest();
        request.setReason("fraud review");

        var blocked = controller.blockUser(7L, request, 1L, "ADMIN", "service-secret");
        var unblocked = controller.unblockUser(7L, 1L, "ADMIN", "service-secret");

        assertThat(blocked.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(unblocked.getStatusCode().is2xxSuccessful()).isTrue();
        verify(userService).blockUser(7L, 1L, request.getReason());
        verify(userService).unblockUser(7L, 1L);
    }
}
