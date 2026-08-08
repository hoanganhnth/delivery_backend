package com.delivery.flashsale_service.controller;

import com.delivery.flashsale_service.dto.*;
import com.delivery.flashsale_service.service.FlashSaleService;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;
import com.delivery.flashsale_service.entity.FlashSaleCampaign;

@RestController
@RequestMapping("/api/flashsales/admin")
@RequiredArgsConstructor
public class AdminFlashSaleController {
    private final FlashSaleService service;

    @PostMapping("/campaigns")
    public ResponseEntity<BaseResponse<FlashSaleCampaignDto>> createCampaign(
            @Valid @RequestBody CreateCampaignRequest req,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireAdmin(actor);
        return ResponseEntity.ok(BaseResponse.success(service.createCampaign(req, actor.getUserId())));
    }

    @GetMapping("/campaigns")
    public ResponseEntity<BaseResponse<List<FlashSaleCampaignDto>>> getAllCampaigns(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireAdmin(actor);
        return ResponseEntity.ok(BaseResponse.success(service.getAllCampaigns()));
    }

    @GetMapping("/campaigns/{id}/items")
    public ResponseEntity<BaseResponse<List<FlashSaleItemDto>>> getCampaignItems(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireAdmin(actor);
        return ResponseEntity.ok(BaseResponse.success(service.getAllItemsByCampaign(id)));
    }

    @PutMapping("/campaigns/{id}/status")
    public ResponseEntity<BaseResponse<Void>> updateStatus(
            @PathVariable Long id,
            @RequestParam FlashSaleCampaign.CampaignStatus status,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireAdmin(actor);
        service.updateCampaignStatus(id, status);
        return ResponseEntity.ok(BaseResponse.success(null));
    }

    @PutMapping("/items/{id}/approve")
    public ResponseEntity<BaseResponse<Void>> approveItem(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireAdmin(actor);
        service.approveItem(id);
        return ResponseEntity.ok(BaseResponse.success(null));
    }

    private void requireAdmin(AuthenticatedActor actor) {
        if (actor == null || !actor.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN role required");
        }
    }
}
