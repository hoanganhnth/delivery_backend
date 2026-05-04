package com.delivery.promotion_service.controller;

import com.delivery.promotion_service.dto.CalculateResponse;
import com.delivery.promotion_service.dto.CartContextRequest;
import com.delivery.promotion_service.dto.CreateVoucherRequest;
import com.delivery.promotion_service.dto.ReserveRequest;
import com.delivery.promotion_service.entity.Voucher;
import com.delivery.promotion_service.service.PromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;

    @PostMapping("/platform")
    public ResponseEntity<Voucher> createPlatformVoucher(@RequestBody @Valid CreateVoucherRequest request,
                                                         @RequestHeader(value = "X-Role", required = false) String role) {
        // Normally check role == ADMIN
        request.setCreatorType(Voucher.CreatorType.PLATFORM);
        request.setCreatorId(null);
        return ResponseEntity.ok(promotionService.createVoucher(request));
    }

    @PostMapping("/merchant")
    public ResponseEntity<Voucher> createMerchantVoucher(@RequestBody @Valid CreateVoucherRequest request,
                                                         @RequestHeader("X-User-Id") Long merchantId) {
        request.setCreatorType(Voucher.CreatorType.MERCHANT);
        request.setCreatorId(merchantId);
        if (request.getScopeType() != Voucher.ScopeType.SHOP) {
            request.setScopeType(Voucher.ScopeType.SHOP);
            request.setScopeRefId(merchantId);
        }
        return ResponseEntity.ok(promotionService.createVoucher(request));
    }

    @PostMapping("/collect/{code}")
    public ResponseEntity<String> collectVoucher(@RequestHeader("X-User-Id") Long userId,
                                                 @PathVariable String code) {
        promotionService.collectVoucher(userId, code);
        return ResponseEntity.ok("Collected successfully");
    }

    @GetMapping("/merchant")
    public ResponseEntity<java.util.List<Voucher>> listMerchantVouchers(@RequestHeader("X-User-Id") Long merchantId) {
        return ResponseEntity.ok(promotionService.listMerchantVouchers(merchantId));
    }

    @GetMapping("/admin")
    public ResponseEntity<java.util.List<Voucher>> listAllVouchers() {
        return ResponseEntity.ok(promotionService.listAllVouchers());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVoucher(@PathVariable Long id) {
        promotionService.deleteVoucher(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/calculate")
    public ResponseEntity<CalculateResponse> calculate(@RequestBody CartContextRequest request) {
        return ResponseEntity.ok(promotionService.calculate(request));
    }

    @PostMapping("/reserve")
    public ResponseEntity<String> reserve(@RequestBody ReserveRequest request) {
        promotionService.reserveVouchers(request);
        return ResponseEntity.ok("Reserved successfully");
    }
}
