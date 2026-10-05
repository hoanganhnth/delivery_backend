package com.delivery.promotion_service.exception;

public class PromotionConflictException extends RuntimeException {
    public PromotionConflictException(String message) {
        super(message);
    }

    public PromotionConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
