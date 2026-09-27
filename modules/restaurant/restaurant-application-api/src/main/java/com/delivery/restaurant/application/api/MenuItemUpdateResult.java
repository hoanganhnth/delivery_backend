package com.delivery.restaurant.application.api;

public record MenuItemUpdateResult(MenuItemSnapshot snapshot, boolean claimedLegacyOwnership) {}
