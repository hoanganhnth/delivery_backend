package com.delivery.user_service.service;

import com.delivery.user_service.dto.UserAddressRequest;
import com.delivery.user_service.dto.UserAddressResponse;

import java.util.List;

public interface UserAddressService {
    List<UserAddressResponse> getAllAddressesByUser(Long userId);

    UserAddressResponse getAddressById(Long id);

    UserAddressResponse createAddress(Long userId, UserAddressRequest request);

    UserAddressResponse updateAddress(Long id, UserAddressRequest request);

    void deleteAddress(Long id);

    UserAddressResponse setDefaultAddress(Long id);
}
