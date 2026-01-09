package com.delivery.livestream_service.exception;

public class ProductAlreadyPinnedException extends RuntimeException {
    
    public ProductAlreadyPinnedException(String message) {
        super(message);
    }
}
