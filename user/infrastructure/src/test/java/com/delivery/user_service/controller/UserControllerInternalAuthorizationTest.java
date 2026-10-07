package com.delivery.user_service.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.user.application.api.UpdateUserBlockStatusCommand;
import com.delivery.user.application.api.UpdateProfileCommand;
import com.delivery.user.application.api.UserBlockStatusUseCase;
import com.delivery.user.application.api.UserProfileReadUseCase;
import com.delivery.user.application.api.UserProfileResult;
import com.delivery.user.application.api.UserProfileUseCase;
import com.delivery.user.application.api.UserRegistrationUseCase;
import com.delivery.user_service.dto.UserRequest;
import com.delivery.user_service.dto.UserResponse;
import com.delivery.user_service.dto.BlockUserRequest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerInternalAuthorizationTest {

    private final UserProfileUseCase userProfileUseCase = mock(UserProfileUseCase.class);
    private final UserProfileReadUseCase userProfileReadUseCase = mock(UserProfileReadUseCase.class);
    private final UserBlockStatusUseCase userBlockStatusUseCase = mock(UserBlockStatusUseCase.class);
    private final UserRegistrationUseCase userRegistrationUseCase = mock(UserRegistrationUseCase.class);
    private final UserController controller = new UserController(
            userProfileUseCase, userProfileReadUseCase, userBlockStatusUseCase, userRegistrationUseCase);

    @BeforeEach
    void configureSecret() {
        ReflectionTestUtils.setField(controller, "internalSecret", "service-secret");
    }

    @Test
    void createUserFailsClosedWithoutServiceCredential() {
        UserRequest request = new UserRequest();

        var response = controller.createUser(request, null);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(userProfileUseCase, never()).create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createUserAcceptsConfiguredServiceCredential() {
        UserRequest request = new UserRequest();
        UserResponse created = UserResponse.builder().id(7L).build();
        when(userProfileUseCase.create(org.mockito.ArgumentMatchers.any()))
                .thenReturn(profileResult(created));

        var response = controller.createUser(request, "service-secret");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(userProfileUseCase).create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void authLookupRejectsWrongServiceCredential() {
        var response = controller.getUserByAuthId(9L, "wrong");

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(userProfileReadUseCase, never()).byAuthId(9L);
    }

    @Test
    void currentProfileUpdateUsesTheGatewayIdentity() {
        UserRequest request = UserRequest.builder().authId(999L).email("ignored@example.com").build();
        UserResponse updated = UserResponse.builder().id(7L).build();
        when(userProfileReadUseCase.byPrincipalId(7L)).thenReturn(profileResult(UserResponse.builder().id(7L).build()));
        when(userProfileUseCase.update(org.mockito.ArgumentMatchers.any())).thenReturn(profileResult(updated));
        AuthenticatedActor actor = new AuthenticatedActor(7L, "user@example.com", Set.of("USER"));

        var response = controller.updateCurrentUser(request, actor);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(userProfileUseCase).update(new UpdateProfileCommand(7L, null, null, null, null, null));
        verify(userProfileUseCase, never()).update(new UpdateProfileCommand(999L, null, null, null, null, null));
    }

    @Test
    void currentProfileReadFailsClosedWithoutTrustedIdentityHeaders() {
        var missingActor = controller.getCurrentUser(null);
        var missingUserActor = controller.updateCurrentUser(new UserRequest(), null);

        assertThat(missingActor.getStatusCode().value()).isEqualTo(403);
        assertThat(missingUserActor.getStatusCode().value()).isEqualTo(403);
        verify(userProfileReadUseCase, never()).byPrincipalId(7L);
        verify(userProfileUseCase, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blockUserChecksRoleBeforePayloadAndBoundsReason() {
        BlockUserRequest tooLong = new BlockUserRequest();
        tooLong.setReason("x".repeat(501));
        tooLong.setAdminId(1L);
        AuthenticatedActor userActor = new AuthenticatedActor(1L, "user@example.com", Set.of("USER"));
        AuthenticatedActor adminActor = new AuthenticatedActor(1L, "admin@example.com", Set.of("ADMIN"));

        var forbidden = controller.blockUser(7L, tooLong, "service-secret", userActor);
        var invalid = controller.blockUser(7L, tooLong, "service-secret", adminActor);

        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        verify(userBlockStatusUseCase, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blockAndUnblockRequireTheAuthServiceCredential() {
        BlockUserRequest request = new BlockUserRequest();
        request.setReason("fraud review");
        request.setAdminId(1L);
        AuthenticatedActor adminActor = new AuthenticatedActor(1L, "admin@example.com", Set.of("ADMIN"));

        var blocked = controller.blockUser(7L, request, null, adminActor);
        var unblocked = controller.unblockUser(7L, request, "wrong", adminActor);

        assertThat(blocked.getStatusCode().value()).isEqualTo(403);
        assertThat(unblocked.getStatusCode().value()).isEqualTo(403);
        verify(userBlockStatusUseCase, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blockAndUnblockAcceptTheAuthServiceCredential() {
        BlockUserRequest request = new BlockUserRequest();
        request.setReason("fraud review");
        request.setAdminId(1L);
        AuthenticatedActor adminActor = new AuthenticatedActor(1L, "admin@example.com", Set.of("ADMIN"));

        var blocked = controller.blockUser(7L, request, "service-secret", adminActor);
        var unblocked = controller.unblockUser(7L, request, "service-secret", adminActor);

        assertThat(blocked.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(unblocked.getStatusCode().is2xxSuccessful()).isTrue();
        verify(userBlockStatusUseCase).update(
                new UpdateUserBlockStatusCommand(7L, 1L, true, request.getReason()));
        verify(userBlockStatusUseCase).update(
                new UpdateUserBlockStatusCommand(7L, 1L, false, null));
    }

    private UserProfileResult profileResult(UserResponse response) {
        return new UserProfileResult(
                response.getId(), response.getAuthId(), response.getPrincipalId(), "ACTIVE", 0L,
                response.getEmail(), response.getRole(), response.getFullName(), response.getPhone(),
                response.getDob(), response.getAvatarUrl(), response.getAddress(), true, false,
                null, null, null, response.getCreatedAt(), response.getUpdatedAt());
    }

    @Test
    void internalOperationsFailClosedWhenSecretIsAbsentOrBlank() {
        for (String secret : new String[] {null, "", " "}) {
            ReflectionTestUtils.setField(controller, "internalSecret", secret);
            var response = controller.getUserByAuthId(9L, "service-secret");
            assertThat(response.getStatusCode().value()).isEqualTo(403);
            assertThat(response.getBody().getMessage()).isEqualTo("Internal service token is required");
        }
        org.mockito.Mockito.verifyNoInteractions(userProfileReadUseCase);
    }

    @Test
    void nullPrincipalCannotReadOrUpdateEvenWhenLegacyIdIsPresent() {
        var actor = new AuthenticatedActor(null, 7L, "user@example.com", Set.of("USER"));
        assertThat(controller.getCurrentUser(actor).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.updateCurrentUser(new UserRequest(), actor).getStatusCode().value()).isEqualTo(403);
        org.mockito.Mockito.verifyNoInteractions(userProfileReadUseCase, userProfileUseCase);
    }

    @Test
    void adminReadsRequireRoleAndMapStatistics() {
        for (AuthenticatedActor actor : new AuthenticatedActor[] {null,
                new AuthenticatedActor(7L, "user@example.com", Set.of("USER"))}) {
            assertThat(controller.getAllUsers(actor).getStatusCode().value()).isEqualTo(403);
            assertThat(controller.getUserStatistics(actor).getBody().getMessage())
                    .isEqualTo("Only ADMIN can access this endpoint");
        }
        org.mockito.Mockito.verifyNoInteractions(userProfileReadUseCase);
        var admin = new AuthenticatedActor(1L, "admin@example.com", Set.of("ADMIN"));
        when(userProfileReadUseCase.all()).thenReturn(java.util.List.of(profileResult(UserResponse.builder().id(7L).email("user@example.com").build())));
        when(userProfileReadUseCase.statistics()).thenReturn(new com.delivery.user.application.api.UserStatisticsResult(10L, 6L, 1L, 2L, 1L, 8L, 2L));
        assertThat(controller.getAllUsers(admin).getBody().getData()).extracting(UserResponse::getEmail)
                .containsExactly("user@example.com");
        assertThat(controller.getUserStatistics(admin).getBody().getData())
                .usingRecursiveComparison().isEqualTo(new com.delivery.user_service.dto.UserStatisticsResponse(10L, 6L, 1L, 2L, 1L, 8L, 2L));
    }

    @Test
    void blockRequiresAdminIdAndReasonWithActorFallback() {
        var admin = new AuthenticatedActor(1L, "admin@example.com", Set.of("ADMIN"));
        var request = new BlockUserRequest();
        assertThat(controller.blockUser(7L, null, "service-secret", null).getBody().getMessage())
                .isEqualTo("Admin ID is required");
        assertThat(controller.blockUser(7L, request, "service-secret", null).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.blockUser(7L, null, "service-secret", admin).getBody().getMessage())
                .isEqualTo("Block reason is required");
        assertThat(controller.blockUser(7L, request, "service-secret", admin).getStatusCode().value()).isEqualTo(400);
        request.setReason("x".repeat(500));
        assertThat(controller.blockUser(7L, request, "service-secret", admin).getStatusCode().value()).isEqualTo(200);
        verify(userBlockStatusUseCase).update(new UpdateUserBlockStatusCommand(7L, 1L, true, "x".repeat(500)));
        request.setAdminId(2L);
        assertThat(controller.blockUser(7L, request, "service-secret", null).getStatusCode().value()).isEqualTo(200);
        verify(userBlockStatusUseCase).update(new UpdateUserBlockStatusCommand(7L, 2L, true, "x".repeat(500)));
    }

    @Test
    void unblockRequiresRoleAndAdminIdWithOptionalPayload() {
        var admin = new AuthenticatedActor(1L, "admin@example.com", Set.of("ADMIN"));
        var user = new AuthenticatedActor(7L, "user@example.com", Set.of("USER"));
        assertThat(controller.unblockUser(7L, null, "service-secret", user).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.unblockUser(7L, null, "service-secret", null).getBody().getMessage())
                .isEqualTo("Admin ID is required");
        assertThat(controller.unblockUser(7L, new BlockUserRequest(), "service-secret", null).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.unblockUser(7L, null, "service-secret", admin).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.unblockUser(7L, new BlockUserRequest(), "service-secret", admin).getStatusCode().value()).isEqualTo(200);
        verify(userBlockStatusUseCase, org.mockito.Mockito.times(2)).update(new UpdateUserBlockStatusCommand(7L, 1L, false, null));
    }
}
