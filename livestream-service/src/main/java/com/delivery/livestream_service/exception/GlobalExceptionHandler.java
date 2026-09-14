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

    private static final String ROOM_NOT_FOUND = "ROOM_NOT_FOUND";
    private static final String INVALID_STATUS = "INVALID_STATUS";
    private static final String OWNERSHIP_DENIED = "OWNERSHIP_DENIED";

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<String>> handleMalformedRequest() {
        return ResponseEntity.badRequest().body(new BaseResponse<>(0, null, "Dữ liệu không hợp lệ"));
    }

    @ExceptionHandler(LivestreamNotFoundException.class)
    public ResponseEntity<BaseResponse<String>> handleLivestreamNotFound(LivestreamNotFoundException ex) {
        log.error("Livestream not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error(ex.getMessage(), ROOM_NOT_FOUND));
    }

    @ExceptionHandler(LivestreamProductNotFoundException.class)
    public ResponseEntity<BaseResponse<String>> handleLivestreamProductNotFound(
            LivestreamProductNotFoundException ex) {
        log.error("Livestream product not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new BaseResponse<>(0, null, ex.getMessage()));
    }

    @ExceptionHandler(InvalidLivestreamStatusException.class)
    public ResponseEntity<BaseResponse<String>> handleInvalidLivestreamStatus(InvalidLivestreamStatusException ex) {
        log.error("Invalid livestream status: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error(ex.getMessage(), INVALID_STATUS));
    }

    @ExceptionHandler(UnauthorizedLivestreamAccessException.class)
    public ResponseEntity<BaseResponse<String>> handleUnauthorizedAccess(UnauthorizedLivestreamAccessException ex) {
        log.error("Unauthorized access: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error(ex.getMessage(), OWNERSHIP_DENIED));
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

    private static BaseResponse<String> error(String message, String code) {
        return new BaseResponse<>(0, null, message, new BaseResponse.ErrorPayload(code));
    }
}
