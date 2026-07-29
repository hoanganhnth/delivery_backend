package com.delivery.promotion_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.delivery.promotion_service.payload.BaseResponse;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PromotionConflictException.class)
    public ResponseEntity<BaseResponse<Void>> handleConflict(PromotionConflictException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<BaseResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<BaseResponse<Void>> handleResponseStatus(ResponseStatusException ex) {
        String message = ex.getReason() == null ? "Request failed" : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode())
                .body(new BaseResponse<>(0, null, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleUnexpected(Exception ex) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi nội bộ. Vui lòng thử lại sau.");
    }

    private ResponseEntity<BaseResponse<Void>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new BaseResponse<>(0, null, message));
    }
}
