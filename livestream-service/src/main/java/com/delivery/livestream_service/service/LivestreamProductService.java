package com.delivery.livestream_service.service;

import com.delivery.livestream_service.dto.event.ProductPinnedEvent;
import com.delivery.livestream_service.dto.event.ProductUnpinnedEvent;
import com.delivery.livestream_service.dto.request.PinProductRequest;
import com.delivery.livestream_service.dto.response.LivestreamProductResponse;
import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.entity.LivestreamProduct;
import com.delivery.livestream_service.enums.LivestreamStatus;
import com.delivery.livestream_service.exception.InvalidLivestreamStatusException;
import com.delivery.livestream_service.exception.LivestreamNotFoundException;
import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
import com.delivery.livestream_service.mapper.LivestreamMapper;
import com.delivery.livestream_service.repository.LivestreamProductRepository;
import com.delivery.livestream_service.repository.LivestreamRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LivestreamProductService {

    private final LivestreamProductRepository productRepository;
    private final LivestreamRepository livestreamRepository;
    private final LivestreamEventPublisher eventPublisher;
    private final LivestreamMapper mapper;

    public LivestreamProductService(LivestreamProductRepository productRepository,
                                    LivestreamRepository livestreamRepository,
                                    LivestreamEventPublisher eventPublisher,
                                    LivestreamMapper mapper) {
        this.productRepository = productRepository;
        this.livestreamRepository = livestreamRepository;
        this.eventPublisher = eventPublisher;
        this.mapper = mapper;
    }

    @Transactional
    public LivestreamProductResponse pinProduct(UUID livestreamId, PinProductRequest request, Long sellerId) {
        log.info("Pinning product: livestream={}, product={}, seller={}", 
                livestreamId, request.getProductId(), sellerId);

        Livestream livestream = getLivestreamAndCheckPermission(livestreamId, sellerId);

        if (livestream.getStatus() != LivestreamStatus.LIVE) {
            throw new InvalidLivestreamStatusException("Chỉ có thể pin sản phẩm khi livestream đang diễn ra");
        }

        // Find or create product
        LivestreamProduct product = productRepository
                .findByLivestreamIdAndProductId(livestreamId, request.getProductId())
                .orElseGet(() -> {
                    LivestreamProduct newProduct = new LivestreamProduct();
                    newProduct.setLivestreamId(livestreamId);
                    newProduct.setProductId(request.getProductId());
                    return newProduct;
                });

        product.setPriceAtLive(request.getPriceAtLive());
        product.setIsPinned(true);
        product.setPinnedAt(LocalDateTime.now());
        product = productRepository.save(product);

        // Publish Kafka event
        ProductPinnedEvent event = new ProductPinnedEvent(
                livestreamId,
                request.getProductId(),
                request.getPriceAtLive(),
                product.getPinnedAt()
        );
        eventPublisher.publishProductPinned(event);

        log.info("Product pinned successfully: livestream={}, product={}", livestreamId, request.getProductId());
        return mapper.toProductResponse(product);
    }

    @Transactional
    public void unpinProduct(UUID livestreamId, Long productId, Long sellerId) {
        log.info("Unpinning product: livestream={}, product={}, seller={}", livestreamId, productId, sellerId);

        Livestream livestream = getLivestreamAndCheckPermission(livestreamId, sellerId);

        if (livestream.getStatus() != LivestreamStatus.LIVE) {
            throw new InvalidLivestreamStatusException("Chỉ có thể bỏ pin sản phẩm khi livestream đang diễn ra");
        }

        LivestreamProduct product = productRepository
                .findByLivestreamIdAndProductId(livestreamId, productId)
                .orElseThrow(() -> new LivestreamNotFoundException(
                        "Không tìm thấy sản phẩm trong livestream"));

        product.setIsPinned(false);
        productRepository.save(product);

        // Publish Kafka event
        ProductUnpinnedEvent event = new ProductUnpinnedEvent(
                livestreamId,
                productId,
                LocalDateTime.now()
        );
        eventPublisher.publishProductUnpinned(event);

        log.info("Product unpinned successfully: livestream={}, product={}", livestreamId, productId);
    }

    @Transactional(readOnly = true)
    public List<LivestreamProductResponse> getProductsByLivestream(UUID livestreamId) {
        log.info("Getting all products for livestream: {}", livestreamId);
        return productRepository.findByLivestreamId(livestreamId)
                .stream()
                .map(mapper::toProductResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LivestreamProductResponse> getPinnedProducts(UUID livestreamId) {
        log.info("Getting pinned products for livestream: {}", livestreamId);
        return productRepository.findByLivestreamIdAndIsPinned(livestreamId, true)
                .stream()
                .map(mapper::toProductResponse)
                .collect(Collectors.toList());
    }

    private Livestream getLivestreamAndCheckPermission(UUID livestreamId, Long sellerId) {
        Livestream livestream = livestreamRepository.findById(livestreamId)
                .orElseThrow(() -> new LivestreamNotFoundException("Không tìm thấy livestream với ID: " + livestreamId));

        if (!livestream.getSellerId().equals(sellerId)) {
            throw new UnauthorizedLivestreamAccessException("Bạn không có quyền thao tác với livestream này");
        }

        return livestream;
    }
}
