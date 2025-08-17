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
}
