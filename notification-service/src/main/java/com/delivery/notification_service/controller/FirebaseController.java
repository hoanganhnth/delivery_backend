package com.delivery.notification_service.controller;

import com.delivery.notification_service.common.constants.HttpHeaderConstants;
import com.delivery.notification_service.payload.BaseResponse;
import com.delivery.notification_service.service.FirebaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * ✅ Firebase Token Controller để manage FCM tokens theo Backend Instructions
 */
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
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestBody TokenRequest request) {

        firebaseService.registerFcmToken(userId, request.getToken());
        return ResponseEntity.ok(new BaseResponse<>(1, null, "Đăng ký FCM token thành công"));
    }

    @PostMapping("/unregister-token")
    public ResponseEntity<BaseResponse<Void>> unregisterFcmToken(
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestBody TokenRequest request) {

        firebaseService.unregisterFcmToken(userId, request.getToken());
        return ResponseEntity.ok(new BaseResponse<>(1, null, "Hủy đăng ký FCM token thành công"));
    }

    public static class TokenRequest {
        private String token;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }
}
