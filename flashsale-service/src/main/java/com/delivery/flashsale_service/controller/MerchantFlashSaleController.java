package com.delivery.flashsale_service.controller;

import com.delivery.flashsale_service.dto.*;
import com.delivery.flashsale_service.service.FlashSaleService;
import com.delivery.flashsale_service.client.RestaurantOwnershipClient;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@RequestMapping("/api/flashsales/merchant")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.flashsale.merchant-registration-enabled", havingValue = "true")
public class MerchantFlashSaleController {
    private final FlashSaleService service;
    private final RestaurantOwnershipClient restaurantOwnershipClient;

    @PostMapping("/items")
    public ResponseEntity<BaseResponse<FlashSaleItemDto>> registerItem(
            @Valid @RequestBody RegisterItemRequest req,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        if (actor == null || !actor.isShopOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SHOP_OWNER role required");
        }
        restaurantOwnershipClient.requireOwnedBy(req.getRestaurantId(), actor.getUserId());
        return ResponseEntity.ok(BaseResponse.success(service.registerItem(req)));
    }
}
