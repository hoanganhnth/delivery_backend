package com.delivery.notification_service.exception;

import com.delivery.notification_service.payload.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * ✅ Global Exception Handler cho Notification Service theo Backend Instructions
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<BaseResponse<Object>> handleRuntimeException(RuntimeException ex) {
        log.error("💥 Runtime error: {}", ex.getMessage(), ex);
        
        BaseResponse<Object> response = new BaseResponse<>(0, null, ex.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<BaseResponse<Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.error("❌ Invalid argument: {}", ex.getMessage());
        
        BaseResponse<Object> response = new BaseResponse<>(0, null, ex.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Object>> handleGenericException(Exception ex) {
        log.error("💥 Unexpected error occurred: {}", ex.getMessage(), ex);
        
        BaseResponse<Object> response = new BaseResponse<>(0, null, "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
