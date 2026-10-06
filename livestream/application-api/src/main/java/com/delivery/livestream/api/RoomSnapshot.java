package com.delivery.livestream.api;
import java.util.UUID;
public record RoomSnapshot(UUID id, Long sellerId, Long restaurantId, String status, Long viewCount) { }
