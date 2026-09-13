package com.delivery.livestream_service.service;

import com.delivery.livestream_service.dto.request.LivestreamCheckoutQuoteRequest;
import com.delivery.livestream_service.dto.response.LivestreamCheckoutQuoteResponse;
import com.delivery.livestream_service.entity.LivestreamProduct;
import com.delivery.livestream_service.enums.LivestreamStatus;
import com.delivery.livestream_service.exception.InvalidLivestreamStatusException;
import com.delivery.livestream_service.exception.LivestreamNotFoundException;
import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
import com.delivery.livestream_service.repository.LivestreamProductRepository;
import com.delivery.livestream_service.repository.LivestreamRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LivestreamCheckoutQuoteService {
    private static final int MAX_PRODUCTS = 50;

    private final LivestreamRepository rooms;
    private final LivestreamProductRepository products;

    public LivestreamCheckoutQuoteService(LivestreamRepository rooms,
                                          LivestreamProductRepository products) {
        this.rooms = rooms;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public LivestreamCheckoutQuoteResponse quote(LivestreamCheckoutQuoteRequest request) {
        validate(request);
        var room = rooms.findById(request.getLivestreamId()).orElseThrow(() ->
                new LivestreamNotFoundException("Không tìm thấy livestream với ID: " + request.getLivestreamId()));
        if (room.getStatus() != LivestreamStatus.LIVE) {
            throw new InvalidLivestreamStatusException("Giá livestream chỉ áp dụng khi phòng đang LIVE");
        }
        if (!request.getRestaurantId().equals(room.getRestaurantId())) {
            throw new UnauthorizedLivestreamAccessException("Restaurant không thuộc livestream");
        }

        Map<Long, LivestreamProduct> pinned = products.findByLivestreamIdAndIsPinned(
                        request.getLivestreamId(), true, PageRequest.of(0, 100)).stream()
                .collect(Collectors.toMap(LivestreamProduct::getProductId, Function.identity()));

        List<LivestreamCheckoutQuoteResponse.Item> quoted = request.getProductIds().stream()
                .map(pinned::get)
                .filter(java.util.Objects::nonNull)
                .map(product -> toQuoteItem(product, request.getRestaurantId()))
                .toList();
        return new LivestreamCheckoutQuoteResponse(
                request.getLivestreamId(), request.getRestaurantId(), quoted);
    }

    private LivestreamCheckoutQuoteResponse.Item toQuoteItem(LivestreamProduct product, Long restaurantId) {
        if (!restaurantId.equals(product.getRestaurantId())) {
            throw new IllegalStateException("Pinned product restaurant scope is invalid");
        }
        if (product.getPriceAtLive() == null || product.getPriceAtLive().signum() <= 0) {
            throw new IllegalStateException("Pinned product price is invalid");
        }
        return new LivestreamCheckoutQuoteResponse.Item(product.getProductId(), product.getPriceAtLive());
    }

    private void validate(LivestreamCheckoutQuoteRequest request) {
        if (request == null || request.getLivestreamId() == null
                || request.getRestaurantId() == null || request.getRestaurantId() <= 0
                || request.getProductIds() == null || request.getProductIds().isEmpty()
                || request.getProductIds().size() > MAX_PRODUCTS
                || request.getProductIds().stream().anyMatch(id -> id == null || id <= 0)
                || new LinkedHashSet<>(request.getProductIds()).size() != request.getProductIds().size()) {
            throw new IllegalArgumentException("Invalid livestream checkout quote scope");
        }
    }
}
