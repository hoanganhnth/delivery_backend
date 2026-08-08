package com.delivery.user_service.controller;

import com.delivery.user_service.dto.UserBlockStatusRequest;
import com.delivery.user_service.payload.BaseResponse;
import com.delivery.user_service.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/users")
public class InternalUserBlockStatusController {

    private final UserService userService;
    private final String internalSecret;

    public InternalUserBlockStatusController(
            UserService userService,
            @Value("${app.internal.secret:}") String internalSecret) {
        this.userService = userService;
        this.internalSecret = internalSecret;
    }

    @PostMapping("/{userId}/block-status")
    public ResponseEntity<BaseResponse<Void>> synchronizeBlockStatus(
            @PathVariable @Positive Long userId,
            @Valid @RequestBody UserBlockStatusRequest request,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {
        if (!isInternalRequest(internalToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new BaseResponse<>(0, null, "Internal service token is required"));
        }
        if (request.blocked() && (request.reason() == null || request.reason().isBlank())) {
            return ResponseEntity.badRequest()
                    .body(new BaseResponse<>(0, null, "Block reason is required"));
        }

        if (request.blocked()) {
            userService.blockUser(userId, request.adminId(), request.reason());
        } else {
            userService.unblockUser(userId, request.adminId());
        }
        return ResponseEntity.ok(new BaseResponse<>(1, null, "User block status synchronized"));
    }

    private boolean isInternalRequest(String internalToken) {
        return internalSecret != null && !internalSecret.isBlank()
                && internalSecret.equals(internalToken);
    }
}
