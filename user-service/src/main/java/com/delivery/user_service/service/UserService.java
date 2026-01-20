package com.delivery.user_service.service;

import java.util.List;

import com.delivery.user_service.dto.UserRequest;
import com.delivery.user_service.dto.UserResponse;
import com.delivery.user_service.dto.UserStatisticsResponse;

public interface UserService {
    UserResponse createUser(UserRequest request);

    UserResponse getUserById(Long id);

    UserResponse getUserByAuthId(Long authId);

    UserResponse updateUser(Long id, UserRequest request);

    void deleteUser(Long id);

    // Admin management methods
    UserStatisticsResponse getUserStatistics();

    List<UserResponse> getAllUsers();

    void blockUser(Long userId, Long adminId, String reason);

    void unblockUser(Long userId, Long adminId);
}
