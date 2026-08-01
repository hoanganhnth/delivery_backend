package com.delivery.user_service.controller;

import org.springframework.http.ResponseEntity;
import com.delivery.user_service.payload.BaseResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import com.delivery.user_service.constant.HttpHeaderConstants;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

import com.delivery.user_service.dto.UserRequest;
import com.delivery.user_service.dto.UserResponse;
import com.delivery.user_service.dto.UserRegistrationRequest;
import com.delivery.user_service.service.UserService;
import com.delivery.user_service.service.UserRegistrationService;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserRegistrationService userRegistrationService;

    public UserController(UserService userService) {
        this(userService, null);
    }

    @Autowired
    public UserController(UserService userService, UserRegistrationService userRegistrationService) {
        this.userService = userService;
        this.userRegistrationService = userRegistrationService;
    }

    @Value("${app.internal.secret:}")
    private String internalSecret;

    @PostMapping
    public ResponseEntity<BaseResponse<UserResponse>> createUser(
            @Valid @RequestBody UserRequest request,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {
        if (!isInternalRequest(internalToken)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Forbidden"));
        }
        UserResponse user = userService.createUser(request);
        return ResponseEntity.ok(new BaseResponse<>(1, user));
    }

    @PostMapping("/registrations")
    public ResponseEntity<BaseResponse<UserResponse>> registerUser(
            @Valid @RequestBody UserRegistrationRequest request) {
        UserResponse user = userRegistrationService.register(request);
        return ResponseEntity.ok(new BaseResponse<>(
                1, user, "User profile registered and linked"));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<UserResponse>> getUserById(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        if (!hasTrustedCurrentUserIdentity(userId, role)) {
            return forbiddenUserAccess();
        }
        UserResponse user = userService.getUserById(userId);
        return ResponseEntity.ok(new BaseResponse<>(1, user));
    }

    @GetMapping("/by-auth/{authId}")
    public ResponseEntity<BaseResponse<UserResponse>> getUserByAuthId(
            @PathVariable Long authId,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {
        if (!isInternalRequest(internalToken)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Forbidden"));
        }
        UserResponse user = userService.getUserByAuthId(authId);
        return ResponseEntity.ok(new BaseResponse<>(1, user));
    }

    @PutMapping
    public ResponseEntity<BaseResponse<UserResponse>> updateCurrentUser(
            @Valid @RequestBody UserRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        if (!hasTrustedCurrentUserIdentity(userId, role)) {
            return forbiddenUserAccess();
        }
        UserResponse user = userService.updateUser(userId, request);
        return ResponseEntity.ok(new BaseResponse<>(1, user));
    }

    private boolean isInternalRequest(String token) {
        return internalSecret != null && !internalSecret.isBlank()
                && token != null && token.equals(internalSecret);
    }

    private boolean hasTrustedCurrentUserIdentity(Long userId, String role) {
        return userId != null && role != null && !role.isBlank();
    }

    private <T> ResponseEntity<BaseResponse<T>> forbiddenUserAccess() {
        return ResponseEntity.status(403)
                .body(new BaseResponse<>(0, null, "Bạn không có quyền truy cập tài khoản này"));
    }

    // Admin endpoints

    /**
     * Get user statistics by role
     */
    @GetMapping("/admin/statistics")
    public ResponseEntity<BaseResponse<com.delivery.user_service.dto.UserStatisticsResponse>> getUserStatistics(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {

        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Only ADMIN can access this endpoint"));
        }

        com.delivery.user_service.dto.UserStatisticsResponse statistics = userService.getUserStatistics();
        return ResponseEntity.ok(new BaseResponse<>(1, statistics));
    }

    /**
     * Get all users
     */
    @GetMapping("/admin/all")
    public ResponseEntity<BaseResponse<java.util.List<UserResponse>>> getAllUsers(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {

        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Only ADMIN can access this endpoint"));
        }

        java.util.List<UserResponse> users = userService.getAllUsers();
        return ResponseEntity.ok(new BaseResponse<>(1, users));
    }

    /**
     * Block a user account
     */
    @PostMapping("/admin/{userId}/block")
    public ResponseEntity<BaseResponse<Void>> blockUser(
            @PathVariable Long userId,
            @RequestBody com.delivery.user_service.dto.BlockUserRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long adminId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {

        if (!"ADMIN".equals(role) || !isInternalRequest(internalToken)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Forbidden"));
        }

        if (adminId == null) {
            return ResponseEntity.status(400)
                    .body(new BaseResponse<>(0, null, "Admin ID is required"));
        }

        if (request == null || request.getReason() == null) {
            return ResponseEntity.status(400)
                    .body(new BaseResponse<>(0, null, "Block reason is required"));
        }
        if (request.getReason().length() > 500) {
            return ResponseEntity.status(400)
                    .body(new BaseResponse<>(0, null, "Block reason must not exceed 500 characters"));
        }

        userService.blockUser(userId, adminId, request.getReason());
        return ResponseEntity.ok(new BaseResponse<>(1, null, "User blocked successfully"));
    }

    /**
     * Unblock a user account
     */
    @PostMapping("/admin/{userId}/unblock")
    public ResponseEntity<BaseResponse<Void>> unblockUser(
            @PathVariable Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long adminId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {

        if (!"ADMIN".equals(role) || !isInternalRequest(internalToken)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Forbidden"));
        }

        if (adminId == null) {
            return ResponseEntity.status(400)
                    .body(new BaseResponse<>(0, null, "Admin ID is required"));
        }

        userService.unblockUser(userId, adminId);
        return ResponseEntity.ok(new BaseResponse<>(1, null, "User unblocked successfully"));
    }
}
