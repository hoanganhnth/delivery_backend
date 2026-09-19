package com.delivery.user_service.service;

import com.delivery.user_service.dto.UserAddressRequest;
import com.delivery.user_service.dto.UserAddressResponse;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;

import java.util.List;

public interface UserAddressService {
    List<UserAddressResponse> getAllAddressesByUser(Long userId);

    UserAddressResponse getAddressById(Long id);

    UserAddressResponse createAddress(Long userId, UserAddressRequest request);

    UserAddressResponse updateAddress(Long id, UserAddressRequest request);

    void deleteAddress(Long id);

    UserAddressResponse setDefaultAddress(Long id);

    default List<UserAddressResponse> getAllAddressesByUser(Long userId, AuthenticatedActor actor) {
        return getAllAddressesByUser(userId);
    }

    default UserAddressResponse getAddressById(Long id, AuthenticatedActor actor) {
        return getAddressById(id);
    }

    default UserAddressResponse createAddress(Long userId, UserAddressRequest request, AuthenticatedActor actor) {
        return createAddress(userId, request);
    }

    default UserAddressResponse updateAddress(Long id, UserAddressRequest request, AuthenticatedActor actor) {
        return updateAddress(id, request);
    }

    default void deleteAddress(Long id, AuthenticatedActor actor) {
        deleteAddress(id);
    }

    default UserAddressResponse setDefaultAddress(Long id, AuthenticatedActor actor) {
        return setDefaultAddress(id);
    }
}
