package com.delivery.restaurant.application.api;

import java.util.List;

public record MenuItemPageSlice(
        List<MenuItemSnapshot> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasNext) {}
