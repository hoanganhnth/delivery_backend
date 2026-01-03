package com.delivery.auth_service.exception;

import com.delivery.auth_service.payload.BaseResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * 🔥 Global Exception Handler cho Auth Service
 * 
 * Xử lý tất cả exceptions và trả về BaseResponse format đồng nhất
 * 
 * @author DeliveryVN Platform
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Xử lý ResourceNotFoundException (404)
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<BaseResponse<Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        BaseResponse<Object> response = new BaseResponse<>();
        response = new BaseResponse<>(0, ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    /**
     * Xử lý AccessDeniedException (403)
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<BaseResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        BaseResponse<Object> response = new BaseResponse<>(0, ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }
    
    /**
     * Xử lý InvalidCredentialsException (401)
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<BaseResponse<Object>> handleInvalidCredentials(InvalidCredentialsException ex) {
        BaseResponse<Object> response = new BaseResponse<>(0, ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
    
    /**
     * Xử lý InvalidTokenException (401)
     */
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<BaseResponse<Object>> handleInvalidToken(InvalidTokenException ex) {
        BaseResponse<Object> response = new BaseResponse<>(0, ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
    
    /**
     * Xử lý EmailAlreadyExistsException (409 Conflict)
     */
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<BaseResponse<Object>> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        BaseResponse<Object> response = new BaseResponse<>(0, ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    
    /**
     * Xử lý validation errors từ @Valid (400)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage())
        );
        
        BaseResponse<Object> response = new BaseResponse<>(0, "Validation failed", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Xử lý IllegalArgumentException (400)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<BaseResponse<Object>> handleIllegalArgument(IllegalArgumentException ex) {
        BaseResponse<Object> response = new BaseResponse<>(0, ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Xử lý tất cả exceptions khác (500)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Object>> handleAllExceptions(Exception ex) {
        ex.printStackTrace(); // Log để debug
        
        BaseResponse<Object> response = new BaseResponse<>(0, "Đã xảy ra lỗi nội bộ. Vui lòng thử lại sau.", null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
