package com.delivery.notification_service.exception;

public class NotificationConflictException extends RuntimeException {

    public NotificationConflictException(String message) {
        super(message);
    }
}
