package com.delivery.user_service.controller;

import org.junit.jupiter.api.Test;

import com.delivery.user_service.dto.UserAddressRequest;
import com.delivery.user_service.dto.UserAddressResponse;
import com.delivery.user_service.service.UserAddressService;

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
        var response = controller.getUserAddresses(22L, 11L, "USER");

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(addressService, never()).getAllAddressesByUser(22L);
    }

    @Test
    void cannotReadAddressOwnedByAnotherUser() {
        when(addressService.getAddressById(7L))
                .thenReturn(UserAddressResponse.builder().id(7L).userId(22L).build());

        var response = controller.getAddress(7L, 11L, "USER");

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().getData()).isNull();
    }

    @Test
    void ownerCanUpdateOwnAddress() {
        UserAddressRequest request = new UserAddressRequest();
        UserAddressResponse address = UserAddressResponse.builder().id(7L).userId(11L).build();
        when(addressService.getAddressById(7L)).thenReturn(address);
        when(addressService.updateAddress(7L, request)).thenReturn(address);

        var response = controller.updateAddress(7L, request, 11L, "USER");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(addressService).updateAddress(7L, request);
    }

    @Test
    void nonCustomerRoleCannotUseCustomerAddressWalletEvenWithSameIdentity() {
        var response = controller.getUserAddresses(11L, 11L, "SHIPPER");

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(addressService, never()).getAllAddressesByUser(11L);
    }

    @Test
    void adminCanDeleteAddress() {
        when(addressService.getAddressById(7L))
                .thenReturn(UserAddressResponse.builder().id(7L).userId(22L).build());

        var response = controller.deleteAddress(7L, 1L, "ADMIN");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(addressService).deleteAddress(7L);
    }
}
