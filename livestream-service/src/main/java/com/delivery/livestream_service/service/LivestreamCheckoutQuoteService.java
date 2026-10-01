package com.delivery.livestream_service.service;

import com.delivery.livestream_service.dto.request.LivestreamCheckoutQuoteRequest;
import com.delivery.livestream_service.dto.response.LivestreamCheckoutQuoteResponse;
import com.delivery.livestream_service.dto.response.LivestreamOrderContext;
import com.delivery.livestream_service.entity.LivestreamCheckoutReceipt;
import com.delivery.livestream_service.entity.LivestreamProduct;
import com.delivery.livestream_service.enums.LivestreamStatus;
import com.delivery.livestream_service.exception.InvalidLivestreamStatusException;
import com.delivery.livestream_service.exception.LivestreamNotFoundException;
import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
import com.delivery.livestream_service.repository.LivestreamCheckoutReceiptRepository;
import com.delivery.livestream_service.repository.LivestreamProductRepository;
import com.delivery.livestream_service.repository.LivestreamRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final LivestreamCheckoutReceiptRepository receipts;
    private final ObjectMapper objectMapper;
    private final LivestreamCheckoutReceiptWriter receiptWriter;

    @org.springframework.beans.factory.annotation.Autowired
    public LivestreamCheckoutQuoteService(LivestreamRepository rooms,
                                          LivestreamProductRepository products,
                                          LivestreamCheckoutReceiptRepository receipts,
                                          ObjectMapper objectMapper,
                                          LivestreamCheckoutReceiptWriter receiptWriter) {
        this.rooms = rooms;
        this.products = products;
        this.receipts = receipts;
        this.objectMapper = objectMapper;
        this.receiptWriter = receiptWriter;
    }

    /** Test seam retaining the production persistence behavior without a Spring proxy. */
    public LivestreamCheckoutQuoteService(LivestreamRepository rooms,
                                          LivestreamProductRepository products,
                                          LivestreamCheckoutReceiptRepository receipts,
                                          ObjectMapper objectMapper) {
        this(rooms, products, receipts, objectMapper, new LivestreamCheckoutReceiptWriter(receipts));
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

        Map<Long, LivestreamProduct> pinned = products.findByLivestreamIdAndIsPinnedTrueAndProductIdIn(
                        request.getLivestreamId(), request.getProductIds()).stream()
                .collect(Collectors.toMap(LivestreamProduct::getProductId, Function.identity()));

        List<LivestreamCheckoutQuoteResponse.Item> quoted = request.getProductIds().stream()
                .map(pinned::get)
                .filter(java.util.Objects::nonNull)
                .map(product -> toQuoteItem(product, request.getRestaurantId()))
                .toList();
        return new LivestreamCheckoutQuoteResponse(
                request.getLivestreamId(), request.getRestaurantId(), quoted);
    }

    public List<LivestreamOrderContext> orderContext(LivestreamCheckoutQuoteRequest request,
                                                     Long actorPrincipalId,
                                                     String correlationId,
                                                     String idempotencyKey) {
        if (actorPrincipalId == null || actorPrincipalId <= 0 || correlationId == null || correlationId.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Livestream checkout context requires actor, correlation and idempotency key");
        }
        validate(request);
        String fingerprint = requestFingerprint(request, actorPrincipalId);
        var existing = receipts.findByActorPrincipalIdAndIdempotencyKey(actorPrincipalId, idempotencyKey);
        if (existing.isPresent()) return restore(existing.get(), fingerprint);
        var room = rooms.findById(request.getLivestreamId()).orElseThrow(() ->
                new LivestreamNotFoundException("Không tìm thấy livestream với ID: " + request.getLivestreamId()));
        if (room.getStatus() != LivestreamStatus.LIVE) {
            throw new InvalidLivestreamStatusException("Checkout chỉ áp dụng khi phòng đang LIVE");
        }
        if (!request.getRestaurantId().equals(room.getRestaurantId())) {
            throw new UnauthorizedLivestreamAccessException("Restaurant không thuộc livestream");
        }
        Map<Long, LivestreamProduct> pinned = products.findByLivestreamIdAndIsPinnedTrueAndProductIdIn(
                        request.getLivestreamId(), request.getProductIds()).stream()
                .collect(Collectors.toMap(LivestreamProduct::getProductId, Function.identity()));
        if (pinned.size() != request.getProductIds().size()) {
            throw new UnauthorizedLivestreamAccessException("Một hoặc nhiều sản phẩm livestream không còn khả dụng");
        }
        List<LivestreamOrderContext> result = request.getProductIds().stream().map(pinned::get)
                .filter(java.util.Objects::nonNull).map(product -> {
                    if (product.getId() == null || product.getPriceAtLive() == null || product.getPriceAtLive().signum() <= 0
                            || !request.getRestaurantId().equals(product.getRestaurantId())) {
                        throw new IllegalStateException("Pinned product snapshot is invalid");
                    }
                    return new LivestreamOrderContext(1, room.getId(), product.getProductId(), room.getSellerId(),
                            room.getRestaurantId(), product.getId(), actorPrincipalId, correlationId,
                            idempotencyKey, product.getPriceAtLive());
                }).toList();
        try {
            receiptWriter.store(new LivestreamCheckoutReceipt(actorPrincipalId, idempotencyKey,
                    fingerprint, serialize(result)));
            return result;
        } catch (DataIntegrityViolationException duplicate) {
            // The database uniqueness fence chooses the first committed snapshot.
            return restore(receipts.findByActorPrincipalIdAndIdempotencyKey(actorPrincipalId, idempotencyKey)
                    .orElseThrow(() -> duplicate), fingerprint);
        }
    }

    private String requestFingerprint(LivestreamCheckoutQuoteRequest request, Long actorPrincipalId) {
        String value = request.getLivestreamId() + ":" + request.getRestaurantId() + ":"
                + request.getProductIds() + ":" + actorPrincipalId;
        return DigestUtils.sha256Hex(value);
    }

    private List<LivestreamOrderContext> restore(LivestreamCheckoutReceipt receipt, String fingerprint) {
        if (!fingerprint.equals(receipt.getRequestFingerprint())) {
            throw new IllegalArgumentException("Idempotency key was already used for another checkout context");
        }
        try {
            return objectMapper.readValue(receipt.getContextPayload(), new TypeReference<>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("Stored livestream checkout receipt is unreadable", exception);
        }
    }

    private String serialize(List<LivestreamOrderContext> contexts) {
        try {
            return objectMapper.writeValueAsString(contexts);
        } catch (Exception exception) {
            throw new IllegalStateException("Livestream checkout receipt cannot be persisted", exception);
        }
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
