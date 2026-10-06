package com.delivery.livestream_service.service;

import com.delivery.livestream_service.dto.event.ProductPinnedEvent;
import com.delivery.livestream_service.dto.event.ProductUnpinnedEvent;
import com.delivery.livestream_service.dto.request.PinProductRequest;
import com.delivery.livestream_service.dto.response.LivestreamProductResponse;
import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.entity.LivestreamProduct;
import com.delivery.livestream_service.exception.LivestreamNotFoundException;
import com.delivery.livestream_service.exception.LivestreamProductNotFoundException;
import com.delivery.livestream_service.mapper.LivestreamMapper;
import com.delivery.livestream_service.repository.LivestreamProductRepository;
import com.delivery.livestream_service.repository.LivestreamRepository;
import lombok.extern.slf4j.Slf4j;
import com.delivery.livestream_service.client.LivestreamProductAuthorityClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.delivery.livestream.api.*;
import com.delivery.livestream.application.ProductUseCases;

@Slf4j
@Service
public class LivestreamProductService {
    private final ProductUseCases<Livestream, LivestreamProduct, LivestreamProductResponse, PinProductRequest> useCases;
    public LivestreamProductService(LivestreamProductRepository products, LivestreamRepository rooms,
                                   LivestreamEventPublisher events, LivestreamMapper mapper,
                                   LivestreamProductAuthorityClient authority) {
        useCases = new ProductUseCases<>(new ProductPorts<>() {
            public Livestream room(UUID id) {
                return rooms.findById(id).orElseThrow(() -> new LivestreamNotFoundException("Không tìm thấy livestream với ID: " + id));
            }
            public RoomSnapshot roomSnapshot(Livestream room) { return LivestreamCompatibility.snapshot(room); }
            public ProductSnapshot snapshot(LivestreamProduct product) { return LivestreamCompatibility.snapshot(product); }
            public LivestreamProduct findOrCreate(UUID id, Long productId) {
                return products.findByLivestreamIdAndProductId(id, productId)
                    .or(() -> products.findByLivestreamIdAndProductIdIncludingDeleted(id, productId))
                    .orElseGet(() -> { var product = new LivestreamProduct(); product.setLivestreamId(id); product.setProductId(productId); return product; });
            }
            public LivestreamProduct find(UUID id, Long productId) {
                return products.findByLivestreamIdAndProductId(id, productId).orElseThrow(() -> new LivestreamProductNotFoundException("Không tìm thấy sản phẩm trong livestream"));
            }
            public void price(LivestreamProduct product, PinProductRequest command) { product.setPriceAtLive(command.getPriceAtLive()); }
            public LivestreamProduct pin(LivestreamProduct product, Livestream room, PinProductRequest command) {
                var canonical = authority.requireAvailable(room.getRestaurantId(), command.getProductId());
                product.setProductName(canonical.productName()); product.setProductImage(canonical.productImage());
                product.setRestaurantId(room.getRestaurantId()); product.setRestaurantName(canonical.restaurantName());
                product.setIsPinned(true); product.setDeletedAt(null); product.setDeletedByPrincipalId(null);
                product.setDeletionReason(null); product.setPinnedAt(LocalDateTime.now());
                return products.save(product);
            }
            public void unpin(LivestreamProduct product) { product.setIsPinned(false); products.save(product); }
            public void remove(LivestreamProduct product, Long legacySeller) {
                product.setIsPinned(false); product.setDeletedAt(LocalDateTime.now());
                product.setDeletedByPrincipalId(legacySeller); product.setDeletionReason("LIVESTREAM_PRODUCT_REMOVED");
                products.save(product);
            }
            public void pinned(UUID id, LivestreamProduct product, PinProductRequest command) {
                events.publishProductPinned(new ProductPinnedEvent(id, command.getProductId(), command.getPriceAtLive(), product.getPinnedAt()));
            }
            public void unpinned(UUID id, Long productId) { events.publishProductUnpinned(new ProductUnpinnedEvent(id, productId, LocalDateTime.now())); }
            public LivestreamProductResponse response(LivestreamProduct product) { return mapper.toProductResponse(product); }
            public List<LivestreamProduct> list(UUID id, boolean pinnedOnly, int limit) {
                var page = PageRequest.of(0, limit);
                return pinnedOnly ? products.findByLivestreamIdAndIsPinned(id, true, page) : products.findByLivestreamId(id, page);
            }
        });
    }
    @Transactional
    public LivestreamProductResponse pinProduct(UUID id, PinProductRequest request, Long seller) { return pinProduct(id, request, seller, false); }
    @Transactional
    public LivestreamProductResponse pinProduct(UUID id, PinProductRequest request, Long seller, boolean admin) {
        return LivestreamCompatibility.call(() -> useCases.pin(id, request, request.getProductId(), request.getRestaurantId(), seller, admin));
    }
    @Transactional
    public void unpinProduct(UUID id, Long product, Long seller) { unpinProduct(id, product, seller, false); }
    @Transactional
    public void unpinProduct(UUID id, Long product, Long seller, boolean admin) {
        LivestreamCompatibility.run(() -> useCases.unpin(id, product, seller, admin));
    }
    @Transactional
    public void removeProduct(UUID id, Long product, Long seller) { removeProduct(id, product, seller, false); }
    @Transactional
    public void removeProduct(UUID id, Long product, Long seller, boolean admin) {
        LivestreamCompatibility.run(() -> useCases.remove(id, product, seller, admin));
    }
    @Transactional(readOnly = true)
    public List<LivestreamProductResponse> getProductsByLivestream(UUID id) { return useCases.list(id, false); }
    @Transactional(readOnly = true)
    public List<LivestreamProductResponse> getPinnedProducts(UUID id) { return useCases.list(id, true); }
}
