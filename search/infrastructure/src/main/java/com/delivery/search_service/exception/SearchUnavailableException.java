package com.delivery.search_service.exception;

public class SearchUnavailableException extends RuntimeException {
    public SearchUnavailableException(String message) {
        super(message);
    }

    public SearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
