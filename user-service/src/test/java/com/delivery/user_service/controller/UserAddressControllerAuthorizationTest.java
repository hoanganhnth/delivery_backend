package com.delivery.user_service.controller;

import org.junit.jupiter.api.Test;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.user_service.dto.UserAddressRequest;
import com.delivery.user_service.dto.UserAddressResponse;
import com.delivery.user_service.service.UserAddressService;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAddressControllerAuthorizationTest {

    private final UserAddressService addressService = mock(UserAddressService.class);
    private final UserAddressController controller = new UserAddressController(addressService);

    @Test
    void cannotListAnotherUsersAddresses() {
        AuthenticatedActor actor = new AuthenticatedActor(11L, "user@example.com", Set.of("USER"));
        var response = controller.getUserAddresses(22L, actor);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(addressService, never()).getAllAddressesByUser(22L);
    }

    @Test
    void cannotReadAddressOwnedByAnotherUser() {
        when(addressService.getAddressById(7L))
                .thenReturn(UserAddressResponse.builder().id(7L).userId(22L).build());
        AuthenticatedActor actor = new AuthenticatedActor(11L, "user@example.com", Set.of("USER"));

        var response = controller.getAddress(7L, actor);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().getData()).isNull();
    }

    @Test
    void ownerCanUpdateOwnAddress() {
        UserAddressRequest request = new UserAddressRequest();
        UserAddressResponse address = UserAddressResponse.builder().id(7L).userId(11L).build();
        when(addressService.getAddressById(7L)).thenReturn(address);
        when(addressService.updateAddress(7L, request)).thenReturn(address);
        AuthenticatedActor actor = new AuthenticatedActor(11L, "user@example.com", Set.of("USER"));

        var response = controller.updateAddress(7L, request, actor);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(addressService).updateAddress(7L, request);
    }

    @Test
    void nonCustomerRoleCannotUseCustomerAddressWalletEvenWithSameIdentity() {
        AuthenticatedActor actor = new AuthenticatedActor(11L, "shipper@example.com", Set.of("SHIPPER"));
        var response = controller.getUserAddresses(11L, actor);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(addressService, never()).getAllAddressesByUser(11L);
    }

    @Test
    void adminCanDeleteAddress() {
        when(addressService.getAddressById(7L))
                .thenReturn(UserAddressResponse.builder().id(7L).userId(22L).build());
        AuthenticatedActor actor = new AuthenticatedActor(1L, "admin@example.com", Set.of("ADMIN"));

        var response = controller.deleteAddress(7L, actor);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(addressService).deleteAddress(7L);
    }
}
