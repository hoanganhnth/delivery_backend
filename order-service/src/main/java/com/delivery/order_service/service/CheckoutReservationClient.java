package com.delivery.order_service.service;

import com.delivery.order_service.dto.request.CreateOrderRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CheckoutReservationClient {
    private final WebClient webClient;
    private final String promotionUrl;
    private final String flashSaleUrl;
    private final String internalSecret;
    private final Duration timeout;

    public CheckoutReservationClient(WebClient webClient,
            @Value("${promotion.service.url:http://promotion-service:8096}") String promotionUrl,
            @Value("${flashsale.service.url:http://flashsale-service:8092}") String flashSaleUrl,
            @Value("${app.internal.secret:}") String internalSecret,
            @Value("${app.checkout.reservation-timeout-ms:2000}") long timeoutMs) {
        this.webClient = webClient; this.promotionUrl = promotionUrl; this.flashSaleUrl = flashSaleUrl;
        this.internalSecret = internalSecret; this.timeout = Duration.ofMillis(timeoutMs);
    }

    public VoucherQuote reserveVoucher(UUID reservationId, Long orderId, Long userId, Long voucherId,
            Long restaurantId, BigDecimal subtotal, BigDecimal shippingFee) {
        return reserveVoucher(reservationId, orderId, userId, null, voucherId, restaurantId, subtotal, shippingFee);
    }

    public VoucherQuote reserveVoucher(UUID reservationId, Long orderId, Long userId, Long userPrincipalId, Long voucherId,
            Long restaurantId, BigDecimal subtotal, BigDecimal shippingFee) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("reservationId", reservationId); request.put("orderId", orderId); request.put("userId", userId);
        if (userPrincipalId != null) request.put("userPrincipalId", userPrincipalId);
        request.put("voucherId", voucherId); request.put("restaurantId", restaurantId);
        request.put("subtotal", subtotal); request.put("shippingFee", shippingFee);
        Map<String, Object> data = post(promotionUrl + "/api/promotions/internal/reserve", request);
        requireIdentity(data, reservationId, orderId, "voucher");
        if (!"RESERVED".equals(text(data.get("state")))) throw new IllegalStateException("Voucher was not reserved");
        return new VoucherQuote(decimal(data.get("discountAmount")));
    }

    @SuppressWarnings("unchecked")
    public VoucherQuote quoteVoucher(Long userId, Long voucherId, Long restaurantId,
                                     BigDecimal subtotal, BigDecimal shippingFee) {
        return quoteVoucher(userId, null, voucherId, restaurantId, subtotal, shippingFee);
    }

    @SuppressWarnings("unchecked")
    public VoucherQuote quoteVoucher(Long userId, Long userPrincipalId, Long voucherId, Long restaurantId,
                                     BigDecimal subtotal, BigDecimal shippingFee) {
        requireSecret();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("shopId", restaurantId); request.put("userId", userId);
        if (userPrincipalId != null) request.put("userPrincipalId", userPrincipalId);
        request.put("subTotal", subtotal); request.put("shippingFee", shippingFee);
        request.put("selectedVoucherId", voucherId);
        Map<String, Object> envelope = webClient.post().uri(promotionUrl + "/api/promotions/internal/calculate")
                .header("Internal-Token", internalSecret)
                .bodyValue(request)
                .retrieve().bodyToMono(Map.class).timeout(timeout).block();
        if (envelope == null || !Integer.valueOf(1).equals(envelope.get("status"))
                || !(envelope.get("data") instanceof Map<?, ?> data))
            throw new IllegalStateException("Voucher quote returned an invalid response");
        return new VoucherQuote(decimal(((Map<String, Object>) data).get("totalDiscount")));
    }

    public FlashQuote quoteFlash(Long restaurantId, List<CreateOrderRequest.OrderItemRequest> requestItems) {
        List<Map<String, Object>> lines = requestItems.stream().filter(item -> item.getFlashSaleItemId() != null)
                .map(item -> Map.<String, Object>of("flashSaleItemId", item.getFlashSaleItemId(),
                        "quantity", item.getQuantity())).toList();
        Map<String, Object> data = post(flashSaleUrl + "/api/flashsales/internal/quote",
                Map.of("restaurantId", restaurantId, "items", lines));
        return parseFlashQuote(data, lines.size());
    }

    public FlashQuote reserveFlash(UUID reservationId, Long orderId, Long userId, Long restaurantId,
            List<CreateOrderRequest.OrderItemRequest> requestItems) {
        return reserveFlash(reservationId, orderId, userId, null, restaurantId, requestItems);
    }

    public FlashQuote reserveFlash(UUID reservationId, Long orderId, Long userId, Long userPrincipalId, Long restaurantId,
            List<CreateOrderRequest.OrderItemRequest> requestItems) {
        List<Map<String, Object>> lines = requestItems.stream().filter(item -> item.getFlashSaleItemId() != null)
                .map(item -> Map.<String, Object>of("flashSaleItemId", item.getFlashSaleItemId(),
                        "quantity", item.getQuantity())).toList();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("reservationId", reservationId); request.put("orderId", orderId); request.put("userId", userId);
        if (userPrincipalId != null) request.put("userPrincipalId", userPrincipalId);
        request.put("restaurantId", restaurantId); request.put("items", lines);
        Map<String, Object> data = post(flashSaleUrl + "/api/flashsales/internal/reserve", request);
        requireIdentity(data, reservationId, orderId, "flash-sale");
        if (!"RESERVED".equals(text(data.get("state")))) throw new IllegalStateException("Flash sale was not reserved");
        return parseFlashQuote(data, lines.size());
    }

    private FlashQuote parseFlashQuote(Map<String, Object> data, int expectedSize) {
        Object rawItems = data.get("items");
        if (!(rawItems instanceof List<?> list)) throw new IllegalStateException("Flash-sale response has no items");
        Map<Long, FlashLine> byItem = list.stream().map(this::flashLine).collect(Collectors.toMap(
                FlashLine::flashSaleItemId, Function.identity()));
        if (byItem.size() != expectedSize) throw new IllegalStateException("Flash-sale response item count mismatch");
        return new FlashQuote(byItem);
    }

    public void releaseVoucher(UUID reservationId, Long orderId) {
        transition(promotionUrl + "/api/promotions/internal/reservations/" + reservationId + "/release?orderId=" + orderId);
    }

    public void releaseFlash(UUID reservationId, Long orderId) {
        transition(flashSaleUrl + "/api/flashsales/internal/reservations/" + reservationId
                + "/release?orderId=" + orderId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String url, Object body) {
        requireSecret();
        Map<String, Object> envelope = webClient.post().uri(url).header("Internal-Token", internalSecret)
                .bodyValue(body).retrieve().bodyToMono(Map.class).timeout(timeout).block();
        if (envelope == null || !Integer.valueOf(1).equals(envelope.get("status"))
                || !(envelope.get("data") instanceof Map<?, ?> data))
            throw new IllegalStateException("Reservation service returned an invalid response");
        return (Map<String, Object>) data;
    }

    private void transition(String url) {
        requireSecret();
        Map<?, ?> envelope = webClient.post().uri(url).header("Internal-Token", internalSecret)
                .retrieve().bodyToMono(Map.class).timeout(timeout).block();
        if (envelope == null || !Integer.valueOf(1).equals(envelope.get("status")))
            throw new IllegalStateException("Reservation release failed");
    }

    private FlashLine flashLine(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalStateException("Invalid flash-sale line");
        return new FlashLine(number(map.get("flashSaleItemId")), number(map.get("menuItemId")),
                ((Number) map.get("quantity")).intValue(), decimal(map.get("unitPrice")));
    }

    private void requireIdentity(Map<String, Object> data, UUID reservationId, Long orderId, String type) {
        if (!reservationId.toString().equals(text(data.get("reservationId")))
                || !orderId.equals(number(data.get("orderId"))))
            throw new IllegalStateException(type + " reservation identity mismatch");
    }

    private Long number(Object value) { return value instanceof Number n ? n.longValue() : Long.valueOf(text(value)); }
    private BigDecimal decimal(Object value) { return value instanceof BigDecimal b ? b : new BigDecimal(text(value)); }
    private String text(Object value) { return value == null ? null : value.toString(); }
    private void requireSecret() { if (internalSecret == null || internalSecret.isBlank()) throw new IllegalStateException("Internal reservation credential is missing"); }

    public record VoucherQuote(BigDecimal discountAmount) {}
    public record FlashQuote(Map<Long, FlashLine> byFlashSaleItemId) {}
    public record FlashLine(Long flashSaleItemId, Long menuItemId, Integer quantity, BigDecimal unitPrice) {}
}
