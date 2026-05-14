package com.delivery.flashsale_service.controller;

import com.delivery.flashsale_service.dto.*;
import com.delivery.flashsale_service.service.FlashSaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/flashsales/merchant")
@RequiredArgsConstructor
public class MerchantFlashSaleController {
    private final FlashSaleService service;

    @PostMapping("/items")
    public ResponseEntity<BaseResponse<FlashSaleItemDto>> registerItem(@RequestBody RegisterItemRequest req) {
        return ResponseEntity.ok(new BaseResponse<>(200, service.registerItem(req), "Success"));
    }
}
