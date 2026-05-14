package com.delivery.flashsale_service.controller;

import com.delivery.flashsale_service.dto.*;
import com.delivery.flashsale_service.service.FlashSaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/flashsales/public")
@RequiredArgsConstructor
public class PublicFlashSaleController {
    private final FlashSaleService service;

    @GetMapping("/campaigns/{campaignId}/items")
    public ResponseEntity<BaseResponse<List<FlashSaleItemDto>>> getItems(@PathVariable Long campaignId) {
        return ResponseEntity.ok(new BaseResponse<>(200, service.getItemsByCampaign(campaignId), "Success"));
    }
}
