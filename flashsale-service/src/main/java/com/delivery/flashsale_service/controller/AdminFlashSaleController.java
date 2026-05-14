package com.delivery.flashsale_service.controller;

import com.delivery.flashsale_service.dto.*;
import com.delivery.flashsale_service.service.FlashSaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flashsales/admin")
@RequiredArgsConstructor
public class AdminFlashSaleController {
    private final FlashSaleService service;

    @PostMapping("/campaigns")
    public ResponseEntity<BaseResponse<FlashSaleCampaignDto>> createCampaign(@RequestBody CreateCampaignRequest req, @RequestHeader("X-User-Id") Long adminId) {
        return ResponseEntity.ok(new BaseResponse<>(200, service.createCampaign(req, adminId), "Success"));
    }

    @GetMapping("/campaigns")
    public ResponseEntity<BaseResponse<List<FlashSaleCampaignDto>>> getAllCampaigns() {
        return ResponseEntity.ok(new BaseResponse<>(200, service.getAllCampaigns(), "Success"));
    }

    @PutMapping("/campaigns/{id}/status")
    public ResponseEntity<BaseResponse<Void>> updateStatus(@PathVariable Long id, @RequestParam String status) {
        service.updateCampaignStatus(id, status);
        return ResponseEntity.ok(new BaseResponse<>(200, null, "Success"));
    }

    @PutMapping("/items/{id}/approve")
    public ResponseEntity<BaseResponse<Void>> approveItem(@PathVariable Long id) {
        service.approveItem(id);
        return ResponseEntity.ok(new BaseResponse<>(200, null, "Success"));
    }
}
