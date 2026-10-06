package com.delivery.notification_service.controller;

import com.delivery.notification_service.exception.NotificationAccessDeniedException;
import com.delivery.notification_service.payload.BaseResponse;
import com.delivery.notification_service.service.FirebaseService;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Slf4j
@RestController
@RequestMapping("/api/firebase")
public class FirebaseController {

    private final FirebaseService firebaseService;

    public FirebaseController(FirebaseService firebaseService) {
        this.firebaseService = firebaseService;
    }

    @PostMapping("/register-token")
    public ResponseEntity<BaseResponse<Void>> registerFcmToken(
            @AuthenticationPrincipal AuthenticatedActor actor,
            @Valid @RequestBody TokenRequest request) {

        requireMobileRole(actor);
        firebaseService.registerFcmToken(actor.getUserId(), request.getToken());
        return ResponseEntity.ok(new BaseResponse<>(1, null, "Đăng ký FCM token thành công"));
    }

    @PostMapping("/unregister-token")
    public ResponseEntity<BaseResponse<Void>> unregisterFcmToken(
            @AuthenticationPrincipal AuthenticatedActor actor,
            @Valid @RequestBody TokenRequest request) {

        requireMobileRole(actor);
        firebaseService.unregisterFcmToken(actor.getUserId(), request.getToken());
        return ResponseEntity.ok(new BaseResponse<>(1, null, "Hủy đăng ký FCM token thành công"));
    }

    private void requireMobileRole(AuthenticatedActor actor) {
        if (actor == null || (!actor.isUser() && !actor.isShipper())) {
            throw new NotificationAccessDeniedException("Forbidden");
        }
    }

    public static class TokenRequest {
        @NotBlank
        @Size(max = 4096)
        private String token;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }
}
