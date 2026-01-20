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

import com.delivery.user_service.dto.UserRequest;
import com.delivery.user_service.dto.UserResponse;
import com.delivery.user_service.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<BaseResponse<UserResponse>> createUser(@Valid @RequestBody UserRequest request) {
        UserResponse user = userService.createUser(request);
        return ResponseEntity.ok(new BaseResponse<>(1, user));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<UserResponse>> getUserById(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) String userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        UserResponse user = userService.getUserById(Long.parseLong(userId));
        return ResponseEntity.ok(new BaseResponse<>(1, user));
    }

    @GetMapping("/by-auth/{authId}")
    public ResponseEntity<BaseResponse<UserResponse>> getUserByAuthId(@PathVariable Long authId) {
        UserResponse user = userService.getUserByAuthId(authId);
        return ResponseEntity.ok(new BaseResponse<>(1, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<UserResponse>> updateUser(@PathVariable Long id,
            @Valid @RequestBody UserRequest request) {
        UserResponse user = userService.updateUser(id, request);
        return ResponseEntity.ok(new BaseResponse<>(1, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(new BaseResponse<>(1, null, "Xóa user thành công"));
    }

    // Admin endpoints

    /**
     * Get user statistics by role
     */
    @GetMapping("/admin/statistics")
    public ResponseEntity<BaseResponse<com.delivery.user_service.dto.UserStatisticsResponse>> getUserStatistics(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {

        // TODO: Add proper authorization check for ADMIN role
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
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {

        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Only ADMIN can block users"));
        }

        if (adminId == null) {
            return ResponseEntity.status(400)
                    .body(new BaseResponse<>(0, null, "Admin ID is required"));
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
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {

        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403)
                    .body(new BaseResponse<>(0, null, "Only ADMIN can unblock users"));
        }

        if (adminId == null) {
            return ResponseEntity.status(400)
                    .body(new BaseResponse<>(0, null, "Admin ID is required"));
        }

        userService.unblockUser(userId, adminId);
        return ResponseEntity.ok(new BaseResponse<>(1, null, "User unblocked successfully"));
    }
}
