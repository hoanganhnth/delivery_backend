package com.delivery.shipper.domain.read;

public record PageRequest(int page, int size) {
    public static final int MAX_SIZE = 100;
    public PageRequest {
        if (page < 0) throw new IllegalArgumentException("page must not be negative");
        if (size < 1 || size > MAX_SIZE) throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
    }
}
