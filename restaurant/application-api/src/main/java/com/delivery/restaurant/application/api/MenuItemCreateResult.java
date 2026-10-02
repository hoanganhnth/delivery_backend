package com.delivery.restaurant.application.api;

public record MenuItemCreateResult(MenuItemSnapshot snapshot, boolean claimedLegacyOwnership) {}
