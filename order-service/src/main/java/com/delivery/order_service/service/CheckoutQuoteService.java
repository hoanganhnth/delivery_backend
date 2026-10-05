package com.delivery.order_service.service;

import com.delivery.order.domain.CheckoutQuotePolicy;

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

    /**
     * Validates a quote without holding an Order DB transaction across the
     * remote restaurant/Promotion calls. The final {@link #consume} call is
     * still the locking authority inside the create-order write transaction.
     */
    public CheckoutPreviewResponse validateAndReprice(CreateOrderRequest request, Long principalId, Long userId) {
        UUID quoteId = request.getQuoteId();
        CheckoutQuote quote = repositoryQuote(quoteId, false);
        decide(() -> CheckoutQuotePolicy.validate(facts(quote), principalId, clock::instant,
                () -> fingerprints.pricingInput(request)));

        CheckoutPreviewRequest previewRequest = toPreviewRequest(request);
        CheckoutPreviewResponse current = previewService.calculatePreview(previewRequest, principalId, userId);
        if (CheckoutQuotePolicy.priceChanged(facts(quote), () -> fingerprints.pricingSnapshot(current))) {
            CheckoutPreviewResponse replacement = issuer.persist(previewRequest, current, principalId);
            throw new OrderApiException("PRICE_CHANGED", "Giá đơn hàng đã thay đổi, vui lòng xác nhận lại",
                    Map.of("quote", replacement));
        }
        return current;
    }

    @Transactional
    public void consume(UUID quoteId, Long principalId, Long orderId) {
        CheckoutQuote quote = repositoryQuote(quoteId, true);
        decide(() -> CheckoutQuotePolicy.consume(facts(quote), principalId, clock::instant));
        quote.consume(orderId);
    }

    private CheckoutPreviewRequest toPreviewRequest(CreateOrderRequest request) {
        CheckoutPreviewRequest preview = new CheckoutPreviewRequest();
        preview.setLivestreamId(request.getLivestreamId());
        preview.setRestaurantId(request.getRestaurantId());
        preview.setDeliveryLat(request.getDeliveryLat());
        preview.setDeliveryLng(request.getDeliveryLng());
        CheckoutQuotePolicy.VoucherSelection selection = CheckoutQuotePolicy.previewSelection(
                request.getVoucherIds(), request.getSelectionMode());
        preview.setVoucherId(selection.voucherId());
        if (selection.includeSelectedIds()) preview.setSelectedVoucherIds(request.getVoucherIds());
        preview.setSelectionMode(request.getSelectionMode());
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

    private CheckoutQuote repositoryQuote(UUID id, boolean lock) {
        if (!lock) decide(() -> CheckoutQuotePolicy.requireId(id));
        CheckoutQuote quote = (lock ? repository.findByIdForUpdate(id) : repository.findById(id)).orElse(null);
        decide(() -> CheckoutQuotePolicy.requireFound(quote == null ? null : facts(quote)));
        return quote;
    }
    private CheckoutQuotePolicy.Quote facts(CheckoutQuote quote) {
        return new CheckoutQuotePolicy.Quote(quote.getPrincipalId(), quote.getExpiresAt(), quote.getConsumedOrderId(),
                quote.getPricingInputFingerprint(), quote.getPricingFingerprint());
    }
    private void decide(Runnable decision) {
        try { decision.run(); }
        catch (CheckoutQuotePolicy.Rejected failure) {
            throw new OrderApiException(failure.code(), failure.getMessage());
        }
    }
}
