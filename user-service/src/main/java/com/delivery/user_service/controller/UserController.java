package com.delivery.user_service.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.user_service.dto.BlockUserRequest;
import com.delivery.user_service.dto.UserRegistrationRequest;
import com.delivery.user_service.dto.UserRequest;
import com.delivery.user_service.dto.UserResponse;
import com.delivery.user_service.dto.UserStatisticsResponse;
import com.delivery.user_service.payload.BaseResponse;
import com.delivery.user_service.service.UserRegistrationService;
import com.delivery.user_service.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserRegistrationService userRegistrationService;

    @Value("${app.internal.secret:}")
    private String internalSecret;

    public UserController(UserService userService) {
        this(userService, null);
    }

    @Autowired
    public UserController(UserService userService, UserRegistrationService userRegistrationService) {
        this.userService = userService;
        this.userRegistrationService = userRegistrationService;
    }

    @PostMapping
    public ResponseEntity<BaseResponse<UserResponse>> createUser(
            @Valid @RequestBody UserRequest request,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {
        if (!isInternalRequest(internalToken)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Internal service token is required"));
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

    @GetMapping("/by-auth/{authId}")
    public ResponseEntity<BaseResponse<UserResponse>> getUserByAuthId(
            @PathVariable Long authId,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {
        if (!isInternalRequest(internalToken)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Internal service token is required"));
        }

        UserResponse user = userService.getUserByAuthId(authId);
        return ResponseEntity.ok(new BaseResponse<>(1, user));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<UserResponse>> getCurrentUser(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        if (actor == null || actor.getUserId() == null) {
            return forbiddenUserAccess();
        }
        UserResponse user = userService.getUserById(actor.getUserId());
        return ResponseEntity.ok(new BaseResponse<>(1, user));
    }

    @PutMapping
    public ResponseEntity<BaseResponse<UserResponse>> updateCurrentUser(
            @Valid @RequestBody UserRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        if (actor == null || actor.getUserId() == null) {
            return forbiddenUserAccess();
        }
        UserResponse user = userService.updateUser(actor.getUserId(), request);
        return ResponseEntity.ok(new BaseResponse<>(1, user));
    }

    private boolean isInternalRequest(String token) {
        return internalSecret != null && !internalSecret.isBlank()
                && token != null && token.equals(internalSecret);
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
    public ResponseEntity<BaseResponse<UserStatisticsResponse>> getUserStatistics(
            @AuthenticationPrincipal AuthenticatedActor actor) {

        if (actor == null || !actor.isAdmin()) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Only ADMIN can access this endpoint"));
        }

        UserStatisticsResponse statistics = userService.getUserStatistics();
        return ResponseEntity.ok(new BaseResponse<>(1, statistics));
    }

    /**
     * Get all users
     */
    @GetMapping("/admin/all")
    public ResponseEntity<BaseResponse<List<UserResponse>>> getAllUsers(
            @AuthenticationPrincipal AuthenticatedActor actor) {

        if (actor == null || !actor.isAdmin()) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Only ADMIN can access this endpoint"));
        }

        List<UserResponse> users = userService.getAllUsers();
        return ResponseEntity.ok(new BaseResponse<>(1, users));
    }

    /**
     * Block a user account (internal auth sync or admin)
     */
    @PostMapping("/admin/{userId}/block")
    public ResponseEntity<BaseResponse<Void>> blockUser(
            @PathVariable Long userId,
            @RequestBody BlockUserRequest request,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken,
            @AuthenticationPrincipal AuthenticatedActor actor) {

        if (!isInternalRequest(internalToken)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Internal service token is required"));
        }

        if (actor != null && !actor.isAdmin()) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Only ADMIN can block users"));
        }

        Long adminId = (request != null && request.getAdminId() != null)
                ? request.getAdminId()
                : (actor != null ? actor.getUserId() : null);

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
     * Unblock a user account (internal auth sync or admin)
     */
    @PostMapping("/admin/{userId}/unblock")
    public ResponseEntity<BaseResponse<Void>> unblockUser(
            @PathVariable Long userId,
            @RequestBody(required = false) BlockUserRequest request,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken,
            @AuthenticationPrincipal AuthenticatedActor actor) {

        if (!isInternalRequest(internalToken)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Internal service token is required"));
        }

        if (actor != null && !actor.isAdmin()) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Only ADMIN can unblock users"));
        }

        Long adminId = (request != null && request.getAdminId() != null)
                ? request.getAdminId()
                : (actor != null ? actor.getUserId() : null);

        if (adminId == null) {
            return ResponseEntity.status(400)
                    .body(new BaseResponse<>(0, null, "Admin ID is required"));
        }

        userService.unblockUser(userId, adminId);
        return ResponseEntity.ok(new BaseResponse<>(1, null, "User unblocked successfully"));
    }
}
