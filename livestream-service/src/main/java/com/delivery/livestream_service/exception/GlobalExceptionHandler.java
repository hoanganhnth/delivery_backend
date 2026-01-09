package com.delivery.livestream_service.exception;

import com.delivery.livestream_service.payload.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LivestreamNotFoundException.class)
    public ResponseEntity<BaseResponse<String>> handleLivestreamNotFound(LivestreamNotFoundException ex) {
        log.error("Livestream not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new BaseResponse<>(0, null, ex.getMessage()));
    }

    @ExceptionHandler(InvalidLivestreamStatusException.class)
    public ResponseEntity<BaseResponse<String>> handleInvalidLivestreamStatus(InvalidLivestreamStatusException ex) {
        log.error("Invalid livestream status: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new BaseResponse<>(0, null, ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedLivestreamAccessException.class)
    public ResponseEntity<BaseResponse<String>> handleUnauthorizedAccess(UnauthorizedLivestreamAccessException ex) {
        log.error("Unauthorized access: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new BaseResponse<>(0, null, ex.getMessage()));
    }

    @ExceptionHandler(ProductAlreadyPinnedException.class)
    public ResponseEntity<BaseResponse<String>> handleProductAlreadyPinned(ProductAlreadyPinnedException ex) {
        log.error("Product already pinned: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new BaseResponse<>(0, null, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.error("Validation errors: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new BaseResponse<>(0, errors, "Dữ liệu không hợp lệ"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<String>> handleGenericException(Exception ex) {
        log.error("Unexpected error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new BaseResponse<>(0, null, "Đã xảy ra lỗi hệ thống"));
    }
}
