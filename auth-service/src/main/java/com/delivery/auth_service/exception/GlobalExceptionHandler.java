package com.delivery.auth_service.exception;

import com.delivery.auth_service.payload.BaseResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * 🔥 Global Exception Handler cho Auth Service
 * 
 * Xử lý tất cả exceptions và trả về BaseResponse format đồng nhất
 * 
 * @author DeliveryVN Platform
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    /**
     * Xử lý ResourceNotFoundException (404)
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<BaseResponse<Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        BaseResponse<Object> response = BaseResponse.failure(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<BaseResponse<Object>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(BaseResponse.failure("Endpoint not found"));
    }
    
    /**
     * Xử lý AccessDeniedException (403)
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<BaseResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        BaseResponse<Object> response = BaseResponse.failure(ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }
    
    /**
     * Xử lý InvalidCredentialsException (401)
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<BaseResponse<Object>> handleInvalidCredentials(InvalidCredentialsException ex) {
        BaseResponse<Object> response = BaseResponse.failure(ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
    
    /**
     * Xử lý InvalidTokenException (401)
     */
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<BaseResponse<Object>> handleInvalidToken(InvalidTokenException ex) {
        BaseResponse<Object> response = BaseResponse.failure(ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
    
    /**
     * Xử lý EmailAlreadyExistsException (409 Conflict)
     */
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<BaseResponse<Object>> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        BaseResponse<Object> response = BaseResponse.failure(ex.getMessage());
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
        
        BaseResponse<Object> response = BaseResponse.failure(errors, "Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Xử lý IllegalArgumentException (400)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<BaseResponse<Object>> handleIllegalArgument(IllegalArgumentException ex) {
        BaseResponse<Object> response = BaseResponse.failure(ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Xử lý tất cả exceptions khác (500)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Object>> handleAllExceptions(Exception ex) {
        log.error("Unhandled auth-service exception", ex);
        
        BaseResponse<Object> response = BaseResponse.failure("Đã xảy ra lỗi nội bộ. Vui lòng thử lại sau.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
