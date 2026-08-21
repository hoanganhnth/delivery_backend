package com.delivery.user_service.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.delivery.user_service.dto.UserRequest;
import com.delivery.user_service.dto.UserResponse;
import com.delivery.user_service.dto.UserStatisticsResponse;
import com.delivery.user_service.entity.User;
import com.delivery.user_service.repository.UserRepository;
import com.delivery.user_service.service.UserService;
import com.delivery.user_service.service.IdentityOutboxService;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final IdentityOutboxService identityOutboxService;

    @Autowired
    public UserServiceImpl(UserRepository userRepository, IdentityOutboxService identityOutboxService) {
        this.userRepository = userRepository;
        this.identityOutboxService = identityOutboxService;
    }

    /** Compatibility seam for legacy unit fixtures; production injects the outbox. */
    public UserServiceImpl(UserRepository userRepository) {
        this(userRepository, null);
    }

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

    public UserResponse getUserByPrincipalId(Long principalId) {
        User user = userRepository.findByPrincipalId(principalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found by principal ID"));
        return toDto(user);
    }

    @Override
    @Transactional
    public UserResponse createUser(UserRequest request) {
        validateProvisioningRequest(request);
        var existing = userRepository.findByPrincipalId(request.getPrincipalId());
        if (existing.isPresent()) {
            User current = requireSameProvisioningIdentity(existing.get(), request);
            publishProfileCreated(current);
            return toDto(current);
        }
        userRepository.findByEmailIgnoreCase(request.getEmail())
                .ifPresent(conflict -> rejectEmailRebinding(conflict, request));

        User user = User.builder()
                .authId(request.getAuthId())
                .principalId(request.getPrincipalId())
                .email(request.getEmail())
                .role(request.getRole())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .dob(request.getDob())
                .avatarUrl(request.getAvatarUrl())
                .address(request.getAddress())
                .build();
        try {
            User saved = userRepository.saveAndFlush(user);
            publishProfileCreated(saved);
            return toDto(saved);
        } catch (DataIntegrityViolationException race) {
            return userRepository.findByPrincipalId(request.getPrincipalId())
                    .map(concurrent -> {
                        User current = requireSameProvisioningIdentity(concurrent, request);
                        publishProfileCreated(current);
                        return toDto(current);
                    })
                    .or(() -> userRepository.findByEmailIgnoreCase(request.getEmail())
                            .map(conflict -> toDto(rejectEmailRebinding(conflict, request))))
                    .orElseThrow(() -> race);
        }
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

    private UserResponse toDto(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .authId(user.getAuthId())
                .principalId(user.getPrincipalId())
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

    private void publishProfileCreated(User user) {
        if (identityOutboxService != null) {
            identityOutboxService.profileCreated(user.getPrincipalId(), user.getId());
        }
    }

    private void validateProvisioningRequest(UserRequest request) {
        if (request.getAuthId() == null || request.getPrincipalId() == null || request.getEmail() == null || request.getEmail().isBlank()
                || request.getRole() == null || request.getRole().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "authId, principalId, email and role are required for user provisioning");
        }
        // principalId is Auth's durable identity key (auth_account.id). authId
        // is retained only as a legacy column/name, so accepting divergent
        // values here could link one profile to two credentials. Every trusted
        // provisioning path must carry the same Auth-owned value.
        if (!request.getAuthId().equals(request.getPrincipalId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "authId and principalId must identify the same Auth account");
        }
    }

    private User requireSameProvisioningIdentity(User existing, UserRequest request) {
        if (!existing.getAuthId().equals(request.getAuthId())
                || !existing.getPrincipalId().equals(request.getPrincipalId())
                || !existing.getEmail().equalsIgnoreCase(request.getEmail())
                || !existing.getRole().equals(request.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "principalId is already linked to a different user identity");
        }
        return existing;
    }

    private User rejectEmailRebinding(User existing, UserRequest request) {
        if (!request.getPrincipalId().equals(existing.getPrincipalId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "email is already linked to a different auth identity");
        }
        return requireSameProvisioningIdentity(existing, request);
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
        return userRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 100)).stream()
                .map(this::toDto)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional
    public void blockUser(Long userId, Long adminId, String reason) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (Boolean.TRUE.equals(user.getIsBlocked())) {
            return;
        }

        user.setIsBlocked(true);
        user.setIsActive(false);
        user.setBlockedAt(java.time.LocalDateTime.now());
        user.setBlockedBy(adminId);
        user.setBlockReason(reason != null ? reason : "No reason provided");

        userRepository.save(user);
    }

    @Override
    @Transactional
    public void unblockUser(Long userId, Long adminId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!Boolean.TRUE.equals(user.getIsBlocked())) {
            return;
        }

        user.setIsBlocked(false);
        user.setIsActive(true);
        user.setBlockedAt(null);
        user.setBlockedBy(null);
        user.setBlockReason(null);

        userRepository.save(user);
    }
}
