package com.delivery.flashsale_service.controller;

import com.delivery.flashsale_service.dto.BaseResponse;
import com.delivery.flashsale_service.dto.ReserveItemRequest;
import com.delivery.flashsale_service.service.FlashSaleStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flashsales/internal")
@RequiredArgsConstructor
public class InternalFlashSaleController {
    
    private final FlashSaleStockService stockService;

    @PostMapping("/reserve")
    public ResponseEntity<BaseResponse<Void>> reserveStock(@RequestBody List<ReserveItemRequest> requests) {
        try {
            stockService.reserveStock(requests);
            return ResponseEntity.ok(new BaseResponse<>(200, null, "Stock reserved successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new BaseResponse<>(400, null, e.getMessage()));
        }
    }
}
