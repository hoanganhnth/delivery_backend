package com.delivery.search_service.dto;

import com.delivery.search_service.document.RestaurantDocument;

public record RestaurantSearchResponse(
        String id,
        String name,
        String description,
        String cuisine,
        Double rating,
        String imageUrl) {

    public static RestaurantSearchResponse from(RestaurantDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Restaurant search document is required");
        }
        return new RestaurantSearchResponse(
                document.getId(),
                document.getName(),
                document.getDescription(),
                document.getCuisine(),
                document.getRating(),
                document.getImageUrl());
    }
}
