package com.delivery.notification_service.exception;

public class NotificationAccessDeniedException extends RuntimeException {

    public NotificationAccessDeniedException(String message) {
        super(message);
    }
}
