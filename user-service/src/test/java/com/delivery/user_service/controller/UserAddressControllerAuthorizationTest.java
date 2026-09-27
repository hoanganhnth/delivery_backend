package com.delivery.user_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.user.application.api.UserAddressResult;
import com.delivery.user.application.api.UserAddressUseCase;
import com.delivery.user.application.api.UserProfileReadUseCase;
import com.delivery.user.application.api.UserProfileResult;
import com.delivery.user_service.dto.UserAddressRequest;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserAddressControllerAuthorizationTest {

    private final UserAddressUseCase userAddressUseCase = mock(UserAddressUseCase.class);
    private final UserProfileReadUseCase userProfileReadUseCase = mock(UserProfileReadUseCase.class);
    private final UserAddressController controller = new UserAddressController(
            userAddressUseCase, userProfileReadUseCase);

    private void configureProfile(Long principalId, Long profileId) {
        when(userProfileReadUseCase.byPrincipalId(principalId)).thenReturn(profile(profileId, principalId));
    }

    @Test
    void cannotListAnotherUsersAddresses() {
        AuthenticatedActor actor = new AuthenticatedActor(11L, "user@example.com", Set.of("USER"));
        configureProfile(11L, 11L);
        var response = controller.getUserAddresses(22L, actor);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(userAddressUseCase, never()).byUserId(22L);
    }

    @Test
    void cannotReadAddressOwnedByAnotherUser() {
        when(userAddressUseCase.byId(7L)).thenReturn(address(7L, 22L));
        AuthenticatedActor actor = new AuthenticatedActor(11L, "user@example.com", Set.of("USER"));
        configureProfile(11L, 11L);

        var response = controller.getAddress(7L, actor);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().getData()).isNull();
    }

    @Test
    void ownerCanUpdateOwnAddress() {
        UserAddressRequest request = new UserAddressRequest();
        UserAddressResult address = address(7L, 11L);
        when(userAddressUseCase.byId(7L)).thenReturn(address);
        when(userAddressUseCase.update(any())).thenReturn(address);
        AuthenticatedActor actor = new AuthenticatedActor(11L, "user@example.com", Set.of("USER"));
        configureProfile(11L, 11L);

        var response = controller.updateAddress(7L, request, actor);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(userAddressUseCase).update(any());
    }

    @Test
    void nonCustomerRoleCannotUseCustomerAddressWalletEvenWithSameIdentity() {
        AuthenticatedActor actor = new AuthenticatedActor(11L, "shipper@example.com", Set.of("SHIPPER"));
        var response = controller.getUserAddresses(11L, actor);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(userAddressUseCase, never()).byUserId(11L);
    }

    @Test
    void adminCanDeleteAddress() {
        when(userAddressUseCase.byId(7L)).thenReturn(address(7L, 22L));
        AuthenticatedActor actor = new AuthenticatedActor(1L, "admin@example.com", Set.of("ADMIN"));

        var response = controller.deleteAddress(7L, actor);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(userAddressUseCase).delete(eq(7L));
    }

    private UserProfileResult profile(Long id, Long principalId) {
        return new UserProfileResult(
                id, principalId, principalId, "ACTIVE", 0L, "user@example.com", "USER",
                "Customer", null, null, null, null, true, false, null, null, null, null, null);
    }

    private UserAddressResult address(Long id, Long userId) {
        return new UserAddressResult(
                id, userId, "Home", "Customer", "0900000000", "Address", "Ward",
                "District", "City", null, null, null, false, null, null);
    }
}
