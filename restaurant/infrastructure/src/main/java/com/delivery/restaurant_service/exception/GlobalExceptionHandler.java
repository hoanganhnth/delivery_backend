package com.delivery.restaurant_service.exception;

import com.delivery.restaurant.domain.decision.RestaurantDecisionConflictException;
import com.delivery.restaurant.domain.rating.RestaurantRatingConflictException;
import com.delivery.restaurant.domain.inventory.InventoryResourceNotFoundException;
import com.delivery.restaurant.domain.serviceability.ServiceabilityResourceNotFoundException;
import com.delivery.restaurant.domain.serviceability.ServiceabilityZoneConflictException;
import com.delivery.restaurant_service.payload.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.stream.Collectors;
import com.delivery.restaurant.domain.ownership.OwnerAssignmentException;
import com.delivery.restaurant.domain.ownership.OwnerAssignmentFailure;


@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Lỗi không tìm thấy
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<BaseResponse<Object>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new BaseResponse<>(0, null, ex.getMessage()));
    }

    @ExceptionHandler({ServiceabilityResourceNotFoundException.class, InventoryResourceNotFoundException.class})
    public ResponseEntity<BaseResponse<Object>> handleInfrastructureNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new BaseResponse<>(0, null, ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<BaseResponse<Object>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new BaseResponse<>(0, null, "Endpoint not found"));
    }


    // ✅ Xử lý không có quyền
    @ExceptionHandler({AccessDeniedException.class, com.delivery.restaurant.domain.serviceability.ServiceabilityAccessDeniedException.class, com.delivery.restaurant.domain.inventory.InventoryAccessDeniedException.class})
    public ResponseEntity<BaseResponse<Object>> handleAccessDenied(RuntimeException ex) {
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

    @ExceptionHandler(ServiceabilityZoneConflictException.class)
    public ResponseEntity<BaseResponse<Object>> handleServiceabilityConflict(
            ServiceabilityZoneConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new BaseResponse<>(0, null, ex.getMessage()));
    }

    @ExceptionHandler(StaleVersionException.class)
    public ResponseEntity<BaseResponse<Object>> handleStaleVersion(StaleVersionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new BaseResponse<>(0, null, ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<BaseResponse<Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(new BaseResponse<>(0, null, ex.getMessage()));
    }

    @ExceptionHandler(OwnerAssignmentException.class)
    public ResponseEntity<BaseResponse<Object>> handleOwnerAssignment(OwnerAssignmentException ex) {
        boolean forbidden = ex.failure() == OwnerAssignmentFailure.ACTOR_NOT_ALLOWED
                || ex.failure() == OwnerAssignmentFailure.CANNOT_ASSIGN_ANOTHER_OWNER;
        String message = forbidden
                ? ex.failure().name()
                : ex.failure() == OwnerAssignmentFailure.OWNER_REQUIRED
                        ? "OWNER_PRINCIPAL_REQUIRED"
                        : "INVALID_OWNER_PRINCIPAL";
        return ResponseEntity.status(forbidden ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST)
                .body(new BaseResponse<>(0, null, message));
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
