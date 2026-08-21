package com.delivery.order_service.service;

import com.delivery.order_service.dto.request.CheckoutPreviewRequest;
import com.delivery.order_service.dto.request.CreateOrderRequest;
import com.delivery.order_service.dto.response.CheckoutPreviewResponse;
import com.delivery.order_service.entity.CheckoutQuote;
import com.delivery.order_service.exception.OrderApiException;
import com.delivery.order_service.repository.CheckoutQuoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Validates the customer-confirmed quote against current canonical pricing. */
@Service
public class CheckoutQuoteService {
    private final CheckoutQuoteRepository repository;
    private final CheckoutQuoteIssuer issuer;
    private final CheckoutPreviewService previewService;
    private final CheckoutFingerprintService fingerprints;
    private final Clock clock;

    public CheckoutQuoteService(CheckoutQuoteRepository repository, CheckoutQuoteIssuer issuer,
                                CheckoutPreviewService previewService,
                                CheckoutFingerprintService fingerprints, Clock clock) {
        this.repository = repository;
        this.issuer = issuer;
        this.previewService = previewService;
        this.fingerprints = fingerprints;
        this.clock = clock;
    }

    public CheckoutPreviewResponse issue(CheckoutPreviewRequest request, Long principalId, Long userId) {
        return issuer.issue(request, principalId, userId);
    }

    @Transactional(readOnly = true)
    public void validateAndReprice(CreateOrderRequest request, Long principalId, Long userId) {
        UUID quoteId = request.getQuoteId();
        if (quoteId == null) {
            throw new OrderApiException("QUOTE_REQUIRED", "Cần báo giá hợp lệ trước khi đặt đơn");
        }
        CheckoutQuote quote = repository.findById(quoteId).orElseThrow(() ->
                new OrderApiException("QUOTE_EXPIRED", "Báo giá không còn hiệu lực"));
        if (!quote.getPrincipalId().equals(principalId)) {
            throw new OrderApiException("QUOTE_MISMATCH", "Báo giá không thuộc khách hàng hiện tại");
        }
        if (!quote.getExpiresAt().isAfter(clock.instant())) {
            throw new OrderApiException("QUOTE_EXPIRED", "Báo giá đã hết hạn, vui lòng xem giá lại");
        }
        if (quote.getConsumedOrderId() != null) {
            throw new OrderApiException("QUOTE_ALREADY_USED", "Báo giá này đã được sử dụng");
        }
        if (!quote.getPricingInputFingerprint().equals(fingerprints.pricingInput(request))) {
            throw new OrderApiException("QUOTE_MISMATCH", "Giỏ hàng hoặc địa điểm giao không khớp báo giá");
        }

        CheckoutPreviewRequest previewRequest = toPreviewRequest(request);
        CheckoutPreviewResponse current = previewService.calculatePreview(previewRequest, principalId, userId);
        if (!quote.getPricingFingerprint().equals(fingerprints.pricingSnapshot(current))) {
            current = issuer.persist(previewRequest, current, principalId);
            throw new OrderApiException("PRICE_CHANGED", "Giá đơn hàng đã thay đổi, vui lòng xác nhận lại",
                    Map.of("quote", current));
        }
    }

    @Transactional
    public void consume(UUID quoteId, Long principalId, Long orderId) {
        CheckoutQuote quote = repository.findByIdForUpdate(quoteId).orElseThrow(() ->
                new OrderApiException("QUOTE_EXPIRED", "Báo giá không còn hiệu lực"));
        if (!quote.getPrincipalId().equals(principalId) || !quote.getExpiresAt().isAfter(clock.instant())) {
            throw new OrderApiException("QUOTE_EXPIRED", "Báo giá không còn hiệu lực");
        }
        if (quote.getConsumedOrderId() != null) {
            throw new OrderApiException("QUOTE_ALREADY_USED", "Báo giá này đã được sử dụng");
        }
        quote.consume(orderId);
    }

    private CheckoutPreviewRequest toPreviewRequest(CreateOrderRequest request) {
        CheckoutPreviewRequest preview = new CheckoutPreviewRequest();
        preview.setRestaurantId(request.getRestaurantId());
        preview.setDeliveryLat(request.getDeliveryLat());
        preview.setDeliveryLng(request.getDeliveryLng());
        if (request.getVoucherIds() != null && request.getVoucherIds().size() == 1) {
            preview.setVoucherId(request.getVoucherIds().get(0));
        }
        preview.setItems((request.getItems() == null ? List.<CreateOrderRequest.OrderItemRequest>of() : request.getItems())
                .stream().map(item -> {
                    CheckoutPreviewRequest.PreviewItem mapped = new CheckoutPreviewRequest.PreviewItem();
                    mapped.setMenuItemId(item.getMenuItemId());
                    mapped.setQuantity(item.getQuantity());
                    mapped.setFlashSaleItemId(item.getFlashSaleItemId());
                    return mapped;
                }).toList());
        return preview;
    }
}
