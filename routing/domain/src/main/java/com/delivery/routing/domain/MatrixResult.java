package com.delivery.routing.domain;

public record MatrixResult(String id, long durationSeconds, long distanceMeters, String source) {
    public MatrixResult {
        if (id == null || id.isBlank() || durationSeconds < 0 || distanceMeters < 0
                || source == null || source.isBlank()) {
            throw new IllegalArgumentException("Matrix result fields are required");
        }
    }
}
