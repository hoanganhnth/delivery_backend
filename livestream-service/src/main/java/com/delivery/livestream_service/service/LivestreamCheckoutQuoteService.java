package com.delivery.livestream_service.service;

import com.delivery.livestream_service.dto.request.LivestreamCheckoutQuoteRequest;
import com.delivery.livestream_service.dto.response.LivestreamCheckoutQuoteResponse;
import com.delivery.livestream_service.dto.response.LivestreamOrderContext;
import com.delivery.livestream_service.entity.LivestreamCheckoutReceipt;
import com.delivery.livestream_service.entity.LivestreamProduct;
import com.delivery.livestream_service.exception.LivestreamNotFoundException;
import com.delivery.livestream_service.repository.LivestreamCheckoutReceiptRepository;
import com.delivery.livestream_service.repository.LivestreamProductRepository;
import com.delivery.livestream_service.repository.LivestreamRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import com.delivery.livestream.api.*;
import com.delivery.livestream.application.CheckoutUseCases;
import com.delivery.livestream_service.entity.Livestream;
import java.util.UUID;
import java.util.Optional;

@Service
public class LivestreamCheckoutQuoteService {
    private final ObjectMapper objectMapper;
    private final CheckoutUseCases<Livestream, LivestreamProduct, LivestreamCheckoutReceipt, LivestreamCheckoutQuoteResponse.Item, LivestreamOrderContext, LivestreamCheckoutQuoteResponse> useCases;
    @org.springframework.beans.factory.annotation.Autowired
    public LivestreamCheckoutQuoteService(LivestreamRepository rooms, LivestreamProductRepository products,
                                         LivestreamCheckoutReceiptRepository receipts, ObjectMapper objectMapper,
                                         LivestreamCheckoutReceiptWriter writer) {
        this.objectMapper = objectMapper;
        useCases = new CheckoutUseCases<>(new CheckoutPorts<>() {
            public Livestream room(UUID id) { return rooms.findById(id).orElseThrow(() -> new LivestreamNotFoundException("Không tìm thấy livestream với ID: " + id)); }
            public RoomSnapshot roomSnapshot(Livestream room) { return LivestreamCompatibility.snapshot(room); }
            public List<LivestreamProduct> pins(UUID id, List<Long> ids) { return products.findByLivestreamIdAndIsPinnedTrueAndProductIdIn(id, ids); }
            public ProductSnapshot snapshot(LivestreamProduct product) { return LivestreamCompatibility.snapshot(product); }
            public Optional<LivestreamCheckoutReceipt> receipt(Long actor, String key) { return receipts.findByActorPrincipalIdAndIdempotencyKey(actor, key); }
            public String fingerprint(LivestreamCheckoutReceipt receipt) { return receipt.getRequestFingerprint(); }
            public List<LivestreamOrderContext> restore(LivestreamCheckoutReceipt receipt) {
                return LivestreamCheckoutQuoteService.this.restore(receipt, receipt.getRequestFingerprint());
            }
            public List<LivestreamOrderContext> store(Long actor, String key, String fingerprint, List<LivestreamOrderContext> result) {
                try {
                    writer.store(new LivestreamCheckoutReceipt(actor, key, fingerprint, serialize(result)));
                    return result;
                } catch (DataIntegrityViolationException duplicate) {
                    return LivestreamCheckoutQuoteService.this.restore(receipts.findByActorPrincipalIdAndIdempotencyKey(actor, key).orElseThrow(() -> duplicate), fingerprint);
                }
            }
            public LivestreamCheckoutQuoteResponse.Item item(LivestreamProduct product) { return new LivestreamCheckoutQuoteResponse.Item(product.getProductId(), product.getPriceAtLive()); }
            public LivestreamOrderContext context(Livestream room, LivestreamProduct product, Long actor, String correlation, String key) {
                return new LivestreamOrderContext(1, room.getId(), product.getProductId(), room.getSellerId(), room.getRestaurantId(), product.getId(), actor, correlation, key, product.getPriceAtLive());
            }
            public LivestreamCheckoutQuoteResponse quote(UUID id, Long restaurant, List<LivestreamCheckoutQuoteResponse.Item> items) { return new LivestreamCheckoutQuoteResponse(id, restaurant, items); }
        });
    }
    /** Test seam retaining the production persistence behavior without a Spring proxy. */
    public LivestreamCheckoutQuoteService(LivestreamRepository rooms, LivestreamProductRepository products,
                                         LivestreamCheckoutReceiptRepository receipts, ObjectMapper mapper) {
        this(rooms, products, receipts, mapper, new LivestreamCheckoutReceiptWriter(receipts));
    }
    private CheckoutCommand command(LivestreamCheckoutQuoteRequest request) {
        return request == null ? null : new CheckoutCommand(request.getLivestreamId(), request.getRestaurantId(), request.getProductIds());
    }
    @Transactional(readOnly = true)
    public LivestreamCheckoutQuoteResponse quote(LivestreamCheckoutQuoteRequest request) {
        return LivestreamCompatibility.call(() -> useCases.quote(command(request)));
    }
    public List<LivestreamOrderContext> orderContext(LivestreamCheckoutQuoteRequest request, Long actor, String correlation, String key) {
        return LivestreamCompatibility.call(() -> useCases.context(command(request), actor, correlation, key));
    }
    private List<LivestreamOrderContext> restore(LivestreamCheckoutReceipt receipt, String fingerprint) {
        com.delivery.livestream.domain.LivestreamPolicy.replay(fingerprint, receipt.getRequestFingerprint());
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

}
