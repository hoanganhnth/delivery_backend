package com.delivery.livestream_service.mapper;

import com.delivery.livestream_service.dto.response.LivestreamProductResponse;
import com.delivery.livestream_service.dto.response.LivestreamResponse;
import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.entity.LivestreamProduct;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LivestreamMapper {

    public LivestreamResponse toResponse(Livestream livestream) {
        if (livestream == null) {
            return null;
        }

        LivestreamResponse response = new LivestreamResponse();
        response.setId(livestream.getId());
        response.setSellerId(livestream.getSellerId());
        response.setRestaurantId(livestream.getRestaurantId());
        response.setTitle(livestream.getTitle());
        response.setDescription(livestream.getDescription());
        response.setStatus(livestream.getStatus());
        response.setStreamProvider(livestream.getStreamProvider());
        response.setRoomId(livestream.getRoomId());
        response.setChannelName(livestream.getChannelName());
        response.setStartedAt(livestream.getStartedAt());
        response.setEndedAt(livestream.getEndedAt());
        response.setViewCount(livestream.getViewCount());
        response.setCreatedAt(livestream.getCreatedAt());
        response.setUpdatedAt(livestream.getUpdatedAt());
        List<LivestreamProductResponse> products = livestream.getProducts() == null
                ? List.of()
                : livestream.getProducts().stream().map(this::toProductResponse).toList();
        response.setPinnedProducts(products);
        return response;
    }

    public LivestreamProductResponse toProductResponse(LivestreamProduct product) {
        if (product == null) {
            return null;
        }

        LivestreamProductResponse response = new LivestreamProductResponse();
        response.setId(product.getId());
        response.setLivestreamId(product.getLivestreamId());
        response.setProductId(product.getProductId());
        response.setProductName(product.getProductName());
        response.setProductImage(product.getProductImage());
        response.setRestaurantId(product.getRestaurantId());
        response.setRestaurantName(product.getRestaurantName());
        response.setPriceAtLive(product.getPriceAtLive());
        response.setIsPinned(product.getIsPinned());
        response.setCreatedAt(product.getCreatedAt());
        response.setPinnedAt(product.getPinnedAt());
        return response;
    }
}
