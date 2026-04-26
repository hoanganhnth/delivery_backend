package com.delivery.order_service.exception;

import com.delivery.order_service.payload.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * ✅ Global Exception Handler cho Order Service theo Backend Instructions
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle custom ValidationException - Single source of validation errors
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<BaseResponse<Object>> handleValidationException(ValidationException ex) {
        log.error("🚨 Validation error: {}", ex.getMessage());
        
        BaseResponse<Object> response = new BaseResponse<>(0, null, ex.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle ResourceNotFoundException
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<BaseResponse<Object>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.error("🔍 Resource not found: {}", ex.getMessage());
        
        BaseResponse<Object> response = new BaseResponse<>(0, null, ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle AccessDeniedException
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<BaseResponse<Object>> handleAccessDeniedException(AccessDeniedException ex) {
        log.error("🚫 Access denied: {}", ex.getMessage());
        
        BaseResponse<Object> response = new BaseResponse<>(0, null, ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * Handle IllegalStateException and IllegalArgumentException
     */
    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<BaseResponse<Object>> handleIllegalStateException(RuntimeException ex) {
        log.error("⚠️ Business rule violation: {}", ex.getMessage());
        
        BaseResponse<Object> response = new BaseResponse<>(0, null, ex.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Object>> handleGenericException(Exception ex) {
        log.error("💥 Unexpected error occurred: {}", ex.getMessage(), ex);
        
        BaseResponse<Object> response = new BaseResponse<>(0, null, "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
