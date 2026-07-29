package com.delivery.search_service.dto;

import com.delivery.search_service.document.DishDocument;

import java.math.BigDecimal;

public record DishSearchResponse(
        String id,
        String name,
        String description,
        BigDecimal price,
        String restaurantId,
        String imageUrl) {

    public static DishSearchResponse from(DishDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Dish search document is required");
        }
        return new DishSearchResponse(
                document.getId(),
                document.getName(),
                document.getDescription(),
                document.getPrice(),
                document.getRestaurantId(),
                document.getImageUrl());
    }
}
