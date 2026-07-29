package com.delivery.flashsale_service.controller;

import com.delivery.flashsale_service.dto.BaseResponse;
import com.delivery.flashsale_service.dto.ReserveItemRequest;
import com.delivery.flashsale_service.service.FlashSaleStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.util.List;
import java.util.Set;

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
    public ResponseEntity<BaseResponse<Void>> reserveStock(
            @RequestBody(required = false) List<ReserveItemRequest> requests,
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
        if (requests == null || requests.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(BaseResponse.failure("At least one reserve item is required"));
        }
        for (ReserveItemRequest request : requests) {
            if (request == null) {
                return ResponseEntity.badRequest()
                        .body(BaseResponse.failure("Reserve item is required"));
            }
            Set<ConstraintViolation<ReserveItemRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(BaseResponse.failure("Invalid reserve item"));
            }
        }
        try {
            FlashSaleStockService stockService = stockServiceProvider.getIfAvailable();
            if (stockService == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(BaseResponse.failure("Flash-sale reservation is unavailable"));
            }
            stockService.reserveStock(requests);
            return ResponseEntity.ok(BaseResponse.success(null, "Stock reserved successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(BaseResponse.failure(e.getMessage()));
        }
    }
}
