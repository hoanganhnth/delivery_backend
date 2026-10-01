package com.delivery.livestream_service.controller;

import com.delivery.livestream_service.dto.request.LivestreamCheckoutQuoteRequest;
import com.delivery.livestream_service.dto.response.LivestreamCheckoutQuoteResponse;
import com.delivery.livestream_service.dto.response.LivestreamOrderContext;
import com.delivery.livestream_service.payload.BaseResponse;
import com.delivery.livestream_service.service.LivestreamCheckoutQuoteService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/livestreams/internal")
@ConditionalOnProperty(name = "app.livestream.api-enabled", havingValue = "true")
public class InternalLivestreamCheckoutController {
    private final LivestreamCheckoutQuoteService service;
    private final String secret;

    public InternalLivestreamCheckoutController(LivestreamCheckoutQuoteService service,
            @Value("${app.internal.secret:}") String secret) {
        this.service = service;
        this.secret = secret;
    }

    @PostMapping("/checkout-quote")
    public ResponseEntity<BaseResponse<LivestreamCheckoutQuoteResponse>> quote(
            @Valid @RequestBody LivestreamCheckoutQuoteRequest request,
            @RequestHeader(value = "Internal-Token", required = false) String token) {
        if (!matchesSecret(token)) {
            return ResponseEntity.status(403).body(new BaseResponse<>(0, null, "Forbidden"));
        }
        return ResponseEntity.ok(new BaseResponse<>(1, service.quote(request),
                "Báo giá livestream hợp lệ"));
    }

    @PostMapping("/order-context")
    public ResponseEntity<BaseResponse<java.util.List<LivestreamOrderContext>>> orderContext(
            @Valid @RequestBody LivestreamCheckoutQuoteRequest request,
            @RequestHeader(value = "Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Actor-Principal-Id", required = false) Long actorPrincipalId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        if (!matchesSecret(token)) {
            return ResponseEntity.status(403).body(new BaseResponse<>(0, null, "Forbidden"));
        }
        return ResponseEntity.ok(new BaseResponse<>(1,
                service.orderContext(request, actorPrincipalId, correlationId, idempotencyKey),
                "Livestream checkout context hợp lệ"));
    }

    private boolean matchesSecret(String token) {
        return secret != null && !secret.isBlank() && token != null
                && MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }
}
