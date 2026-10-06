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
            userAddressUseCase, new com.delivery.user.application.DefaultUserAddressAccessUseCase(userProfileReadUseCase));

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

    @Test
    void ownerCanCreateListReadAndSelectDefaultAddressWithMappedFields() {
        var actor = new AuthenticatedActor(110L, "user@example.com", Set.of("USER"));
        configureProfile(110L, 11L);
        var saved = address(7L, 11L);
        var request = new UserAddressRequest();
        request.setLabel("Home");
        request.setRecipientName("Customer");
        request.setPhoneNumber("0900000000");
        request.setAddressLine("Address");
        request.setWard("Ward");
        request.setDistrict("District");
        request.setCity("City");
        request.setIsDefault(false);
        when(userAddressUseCase.create(any())).thenReturn(saved);
        when(userAddressUseCase.byUserId(11L)).thenReturn(java.util.List.of(saved));
        when(userAddressUseCase.byId(7L)).thenReturn(saved);
        when(userAddressUseCase.setDefault(7L)).thenReturn(saved);

        var created = controller.createAddress(11L, request, actor);
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        assertThat(created.getBody().getStatus()).isEqualTo(1);
        assertThat(created.getBody().getData().getAddressLine()).isEqualTo("Address");
        verify(userAddressUseCase).create(new com.delivery.user.application.api.CreateUserAddressCommand(
                11L, "Home", "Customer", "0900000000", "Address", "Ward", "District", "City",
                null, null, null, false));
        assertThat(controller.getUserAddresses(11L, actor).getBody().getData())
                .singleElement().satisfies(response -> {
                    assertThat(response.getId()).isEqualTo(7L);
                    assertThat(response.getUserId()).isEqualTo(11L);
                });
        assertThat(controller.getAddress(7L, actor).getBody().getData().getCity()).isEqualTo("City");
        assertThat(controller.setDefault(7L, actor).getStatusCode().value()).isEqualTo(200);
        verify(userAddressUseCase).setDefault(7L);
    }

    @Test
    void forbiddenMutationsNeverReachTheUseCase() {
        var actor = new AuthenticatedActor(11L, "user@example.com", Set.of("USER"));
        configureProfile(11L, 11L);
        when(userAddressUseCase.byId(7L)).thenReturn(address(7L, 22L));
        assertThat(controller.createAddress(22L, new UserAddressRequest(), actor).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.updateAddress(7L, new UserAddressRequest(), actor).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.setDefault(7L, actor).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.deleteAddress(7L, actor).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.getUserAddresses(11L, null).getStatusCode().value()).isEqualTo(403);
        verify(userAddressUseCase, never()).create(any());
        verify(userAddressUseCase, never()).update(any());
        verify(userAddressUseCase, never()).setDefault(any());
        verify(userAddressUseCase, never()).delete(any());
    }

    @Test
    void useCaseStatusErrorsRetainCurrentForbiddenEnvelope() {
        var actor = new AuthenticatedActor(1L, "admin@example.com", Set.of("ADMIN"));
        var failure = new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        when(userAddressUseCase.byId(7L)).thenReturn(address(7L, 22L));
        when(userAddressUseCase.byUserId(22L)).thenThrow(failure);
        when(userAddressUseCase.create(any())).thenThrow(failure);
        when(userAddressUseCase.update(any())).thenThrow(failure);
        when(userAddressUseCase.setDefault(7L)).thenThrow(failure);
        org.mockito.Mockito.doThrow(failure).when(userAddressUseCase).delete(7L);
        var responses = java.util.List.of(
                controller.getUserAddresses(22L, actor),
                controller.createAddress(22L, new UserAddressRequest(), actor),
                controller.updateAddress(7L, new UserAddressRequest(), actor),
                controller.setDefault(7L, actor), controller.deleteAddress(7L, actor));
        assertThat(responses).allSatisfy(response -> {
            assertThat(response.getStatusCode().value()).isEqualTo(403);
            assertThat(response.getBody().getStatus()).isEqualTo(0);
            assertThat(response.getBody().getData()).isNull();
            assertThat(response.getBody().getMessage()).isEqualTo("Bạn không có quyền truy cập địa chỉ này");
        });
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
