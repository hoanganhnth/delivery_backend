package com.delivery.user_service.service;

import com.delivery.user_service.dto.UserRequest;
import com.delivery.user_service.dto.UserResponse;

public interface UserService {
    UserResponse createUser(UserRequest request);

    UserResponse getUserById(Long id);

    UserResponse getUserByAuthId(Long authId);

    UserResponse updateUser(Long id, UserRequest request);

    void deleteUser(Long id);
}
