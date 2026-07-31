package com.delivery.flashsale_service.controller;

import com.delivery.flashsale_service.dto.BaseResponse;
import com.delivery.flashsale_service.dto.FlashSaleReservationRequest;
import com.delivery.flashsale_service.dto.FlashSaleReservationResponse;
import com.delivery.flashsale_service.dto.FlashSaleQuoteRequest;
import com.delivery.flashsale_service.dto.FlashSaleQuoteResponse;
import com.delivery.flashsale_service.service.FlashSaleStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.validation.Validator;

import java.util.UUID;

@RestController
@RequestMapping("/api/flashsales/internal")
@RequiredArgsConstructor
public class InternalFlashSaleController {
    
    private final ObjectProvider<FlashSaleStockService> stockServiceProvider;
    private final Validator validator;

    @Value("${app.internal.secret:}")
    private String internalSecret;

    @Value("${app.flashsale.checkout-enabled:false}")
    private boolean checkoutEnabled;

    @PostMapping("/reserve")
    public ResponseEntity<BaseResponse<FlashSaleReservationResponse>> reserveStock(
            @RequestBody(required = false) FlashSaleReservationRequest request,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {
        if (internalSecret == null || internalSecret.isBlank()
                || !internalSecret.equals(internalToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(BaseResponse.failure("Forbidden"));
        }
        if (!checkoutEnabled) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(BaseResponse.failure(
                            "Flash-sale checkout is disabled until reservation recovery is proven"));
        }
        if (request == null || !validator.validate(request).isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(BaseResponse.failure("Invalid flash sale reservation request"));
        }
        try {
            FlashSaleStockService stockService = stockServiceProvider.getIfAvailable();
            if (stockService == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(BaseResponse.failure("Flash-sale reservation is unavailable"));
            }
            return ResponseEntity.ok(BaseResponse.success(stockService.reserveStock(request),
                    "Stock reserved successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(BaseResponse.failure(e.getMessage()));
        }
    }

    @PostMapping("/quote")
    public ResponseEntity<BaseResponse<FlashSaleQuoteResponse>> quote(
            @RequestBody(required = false) FlashSaleQuoteRequest request,
            @RequestHeader(value = "Internal-Token", required = false) String token) {
        if (internalSecret == null || internalSecret.isBlank() || !internalSecret.equals(token))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(BaseResponse.failure("Forbidden"));
        if (!checkoutEnabled || stockServiceProvider.getIfAvailable() == null)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(BaseResponse.failure("Flash-sale quote is unavailable"));
        if (request == null || !validator.validate(request).isEmpty())
            return ResponseEntity.badRequest().body(BaseResponse.failure("Invalid flash-sale quote request"));
        return ResponseEntity.ok(BaseResponse.success(stockServiceProvider.getObject().quote(request), "Quoted"));
    }

    @PostMapping("/reservations/{reservationId}/commit")
    public ResponseEntity<BaseResponse<FlashSaleReservationResponse>> commit(
            @PathVariable UUID reservationId, @RequestParam Long orderId,
            @RequestHeader(value = "Internal-Token", required = false) String token) {
        ResponseEntity<BaseResponse<FlashSaleReservationResponse>> denied = requireInternal(token);
        if (denied != null) return denied;
        return ResponseEntity.ok(BaseResponse.success(
                stockServiceProvider.getObject().commit(reservationId, orderId), "Committed"));
    }

    @PostMapping("/reservations/{reservationId}/release")
    public ResponseEntity<BaseResponse<FlashSaleReservationResponse>> release(
            @PathVariable UUID reservationId, @RequestParam Long orderId,
            @RequestHeader(value = "Internal-Token", required = false) String token) {
        ResponseEntity<BaseResponse<FlashSaleReservationResponse>> denied = requireInternal(token);
        if (denied != null) return denied;
        return ResponseEntity.ok(BaseResponse.success(
                stockServiceProvider.getObject().release(reservationId, orderId), "Released"));
    }

    private ResponseEntity<BaseResponse<FlashSaleReservationResponse>> requireInternal(String token) {
        if (internalSecret == null || internalSecret.isBlank() || !internalSecret.equals(token))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(BaseResponse.failure("Forbidden"));
        if (!checkoutEnabled)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(BaseResponse.failure("Flash-sale checkout is disabled"));
        if (stockServiceProvider.getIfAvailable() == null)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(BaseResponse.failure("Flash-sale reservation is unavailable"));
        return null;
    }
}
