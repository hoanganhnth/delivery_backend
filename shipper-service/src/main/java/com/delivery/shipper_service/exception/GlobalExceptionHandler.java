package com.delivery.shipper_service.exception;

import com.delivery.shipper_service.payload.BaseResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Lỗi không tìm thấy
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<BaseResponse<Object>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new BaseResponse<>(0, null, ex.getMessage()));
    }

    // Xử lý không có quyền
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<BaseResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new BaseResponse<>(0, null, ex.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<BaseResponse<Object>> handleBadRequest(Exception ex) {
        return ResponseEntity.badRequest()
                .body(new BaseResponse<>(0, null, ex.getMessage()));
    }

    // Lỗi chung khác
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Object>> handleAll(Exception ex) {
        log.error("Unhandled shipper-service error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new BaseResponse<>(0, null, "Đã xảy ra lỗi nội bộ."));
    }
}
