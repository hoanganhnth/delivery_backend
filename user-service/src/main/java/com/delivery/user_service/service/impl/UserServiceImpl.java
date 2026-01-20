package com.delivery.user_service.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.delivery.user_service.dto.UserRequest;
import com.delivery.user_service.dto.UserResponse;
import com.delivery.user_service.dto.UserStatisticsResponse;
import com.delivery.user_service.entity.User;
import com.delivery.user_service.repository.UserRepository;
import com.delivery.user_service.service.UserService;

import lombok.RequiredArgsConstructor;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return toDto(user);
    }

    @Override
    public UserResponse getUserByAuthId(Long authId) {
        User user = userRepository.findByAuthId(authId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found by auth ID"));
        return toDto(user);
    }

    @Override
    public UserResponse createUser(UserRequest request) {
        User user = User.builder()
                .authId(request.getAuthId())
                .email(request.getEmail())
                .role(request.getRole())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .dob(request.getDob())
                .avatarUrl(request.getAvatarUrl())
                .address(request.getAddress())
                .build();
        return toDto(userRepository.save(user));
    }

    @Override
    public UserResponse updateUser(Long id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setDob(request.getDob());
        user.setAvatarUrl(request.getAvatarUrl());
        user.setAddress(request.getAddress());
        // Không update authId, email, role để bảo vệ tính đồng bộ với AuthService

        return toDto(userRepository.save(user));
    }

    @Override
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        userRepository.deleteById(id);
    }

    private UserResponse toDto(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .authId(user.getAuthId())
                .email(user.getEmail())
                .role(user.getRole())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .dob(user.getDob())
                .avatarUrl(user.getAvatarUrl())
                .address(user.getAddress())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    @Override
    public UserStatisticsResponse getUserStatistics() {
        long totalUsers = userRepository.count();
        long userCount = userRepository.countByRole("USER");
        long adminCount = userRepository.countByRole("ADMIN");
        long shipperCount = userRepository.countByRole("SHIPPER");
        long shopOwnerCount = userRepository.countByRole("SHOP_OWNER");
        long activeUsers = userRepository.countByIsActive(true);
        long blockedUsers = userRepository.countByIsBlocked(true);

        return UserStatisticsResponse.builder()
                .totalUsers(totalUsers)
                .userCount(userCount)
                .adminCount(adminCount)
                .shipperCount(shipperCount)
                .shopOwnerCount(shopOwnerCount)
                .activeUsers(activeUsers)
                .blockedUsers(blockedUsers)
                .build();
    }

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public void blockUser(Long userId, Long adminId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getIsBlocked()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is already blocked");
        }

        user.setIsBlocked(true);
        user.setIsActive(false);
        user.setBlockedAt(java.time.LocalDateTime.now());
        user.setBlockedBy(adminId);
        user.setBlockReason(reason != null ? reason : "No reason provided");

        userRepository.save(user);
    }

    @Override
    public void unblockUser(Long userId, Long adminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!user.getIsBlocked()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not blocked");
        }

        user.setIsBlocked(false);
        user.setIsActive(true);
        user.setBlockedAt(null);
        user.setBlockedBy(null);
        user.setBlockReason(null);

        userRepository.save(user);
    }
}
