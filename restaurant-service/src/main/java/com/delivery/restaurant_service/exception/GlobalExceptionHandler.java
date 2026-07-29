package com.delivery.restaurant_service.exception;

import com.delivery.restaurant_service.payload.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.stream.Collectors;


@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Lỗi không tìm thấy
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<BaseResponse<Object>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new BaseResponse<>(0, null, ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<BaseResponse<Object>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new BaseResponse<>(0, null, "Endpoint not found"));
    }


    // ✅ Xử lý không có quyền
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<BaseResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new BaseResponse<>(0, null, ex.getMessage()));
    }

    @ExceptionHandler(RestaurantDecisionConflictException.class)
    public ResponseEntity<BaseResponse<Object>> handleDecisionConflict(RestaurantDecisionConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new BaseResponse<>(0, null, ex.getMessage()));
    }

    @ExceptionHandler(RestaurantRatingConflictException.class)
    public ResponseEntity<BaseResponse<Object>> handleRatingConflict(RestaurantRatingConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new BaseResponse<>(0, null, ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<BaseResponse<Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(new BaseResponse<>(0, null, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(new BaseResponse<>(0, null, message));
    }

    // Lỗi chung khác
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Object>> handleAll(Exception ex) {
        log.error("Unhandled restaurant-service error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new BaseResponse<>(0, null, "Đã xảy ra lỗi nội bộ."));
    }
}
