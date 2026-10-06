package com.delivery.livestream_service.service;
import com.delivery.livestream.api.RoomSnapshot;
import com.delivery.livestream.api.ProductSnapshot;
import com.delivery.livestream.domain.LivestreamPolicy;
import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.entity.LivestreamProduct;
import com.delivery.livestream_service.exception.*;
import java.util.function.Supplier;

final class LivestreamCompatibility {
    private LivestreamCompatibility() { }
    static String name(Enum<?> value) { return value == null ? null : value.name(); }
    static RoomSnapshot snapshot(Livestream room) {
        return new RoomSnapshot(room.getId(), room.getSellerId(), room.getRestaurantId(), name(room.getStatus()), room.getViewCount());
    }
    static ProductSnapshot snapshot(LivestreamProduct product) {
        return new ProductSnapshot(product.getId(), product.getProductId(), product.getRestaurantId(), product.getPriceAtLive(), product.getIsPinned());
    }
    static <T> T call(Supplier<T> action) {
        try { return action.get(); }
        catch (LivestreamPolicy.Rejection failure) {
            throw switch (failure.failure()) {
                case STATUS -> new InvalidLivestreamStatusException(failure.getMessage());
                case PERMISSION -> new UnauthorizedLivestreamAccessException(failure.getMessage());
                case DUPLICATE -> new ProductAlreadyPinnedException(failure.getMessage());
            };
        }
    }
    static void run(Runnable action) { call(() -> { action.run(); return null; }); }
}
