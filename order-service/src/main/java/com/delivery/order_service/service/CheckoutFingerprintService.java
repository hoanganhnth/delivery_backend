package com.delivery.order_service.service;

import com.delivery.order_service.dto.request.CheckoutPreviewRequest;
import com.delivery.order_service.dto.request.CreateOrderRequest;
import com.delivery.order_service.dto.response.CheckoutPreviewResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/** Versioned, deterministic hashes for quote and idempotency comparison. */
@Service
public class CheckoutFingerprintService {
    public static final String VERSION = "v1";

    public String pricingInput(CheckoutPreviewRequest request) {
        StringBuilder value = new StringBuilder(VERSION);
        token(value, request.getRestaurantId());
        token(value, decimal(request.getDeliveryLat()));
        token(value, decimal(request.getDeliveryLng()));
        token(value, request.getVoucherId());
        for (CheckoutPreviewRequest.PreviewItem item : sortedPreviewItems(request.getItems())) {
            token(value, item.getMenuItemId());
            token(value, item.getQuantity());
            token(value, item.getFlashSaleItemId());
        }
        return hash(value);
    }

    public String pricingInput(CreateOrderRequest request) {
        StringBuilder value = new StringBuilder(VERSION);
        token(value, request.getRestaurantId());
        token(value, decimal(request.getDeliveryLat()));
        token(value, decimal(request.getDeliveryLng()));
        List<Long> vouchers = request.getVoucherIds() == null ? List.of()
                : request.getVoucherIds().stream().sorted().toList();
        // Current checkout allows at most one voucher; keep the preview and
        // create representations byte-for-byte equivalent for that contract.
        token(value, vouchers.size() == 1 ? vouchers.get(0) : null);
        for (CreateOrderRequest.OrderItemRequest item : sortedOrderItems(request.getItems())) {
            token(value, item.getMenuItemId());
            token(value, item.getQuantity());
            token(value, item.getFlashSaleItemId());
        }
        return hash(value);
    }

    public String pricingSnapshot(CheckoutPreviewResponse response) {
        StringBuilder value = new StringBuilder(VERSION);
        token(value, response.getRestaurantId());
        for (CheckoutPreviewResponse.PreviewItemDetail item : response.getItems() == null ? List.<CheckoutPreviewResponse.PreviewItemDetail>of()
                : response.getItems().stream().sorted(Comparator.comparing(CheckoutPreviewResponse.PreviewItemDetail::getMenuItemId,
                Comparator.nullsFirst(Comparator.naturalOrder()))).toList()) {
            token(value, item.getMenuItemId());
            token(value, item.getQuantity());
            token(value, decimal(item.getUnitPrice()));
            token(value, decimal(item.getLineTotal()));
        }
        token(value, decimal(response.getSubtotal()));
        token(value, decimal(response.getShippingFee()));
        token(value, decimal(response.getDiscountAmount()));
        token(value, decimal(response.getTotalPrice()));
        return hash(value);
    }

    public String createCommand(CreateOrderRequest request) {
        StringBuilder value = new StringBuilder(VERSION);
        token(value, request.getQuoteId());
        token(value, pricingInput(request));
        token(value, request.getDeliveryAddress());
        token(value, request.getCustomerName());
        token(value, request.getCustomerPhone());
        token(value, request.getPaymentMethod());
        token(value, request.getNotes());
        for (CreateOrderRequest.OrderItemRequest item : sortedOrderItems(request.getItems())) {
            token(value, item.getMenuItemId());
            token(value, item.getQuantity());
            token(value, item.getFlashSaleItemId());
            token(value, item.getNotes());
        }
        return hash(value);
    }

    private List<CheckoutPreviewRequest.PreviewItem> sortedPreviewItems(List<CheckoutPreviewRequest.PreviewItem> items) {
        return items == null ? List.of() : items.stream().sorted(Comparator.comparing(
                CheckoutPreviewRequest.PreviewItem::getMenuItemId, Comparator.nullsFirst(Comparator.naturalOrder()))).toList();
    }

    private List<CreateOrderRequest.OrderItemRequest> sortedOrderItems(List<CreateOrderRequest.OrderItemRequest> items) {
        return items == null ? List.of() : items.stream().sorted(Comparator.comparing(
                CreateOrderRequest.OrderItemRequest::getMenuItemId, Comparator.nullsFirst(Comparator.naturalOrder()))).toList();
    }

    private void token(StringBuilder target, Object value) {
        String text = value == null ? "<null>" : value.toString();
        target.append('|').append(Base64.getUrlEncoder().withoutPadding()
                .encodeToString(text.getBytes(StandardCharsets.UTF_8)));
    }

    private String decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String hash(StringBuilder value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
