package com.delivery.promotion_service.controller;

import com.delivery.promotion_service.dto.CalculateResponse;
import com.delivery.promotion_service.dto.CartContextRequest;
import com.delivery.promotion_service.dto.CreateVoucherRequest;
import com.delivery.promotion_service.dto.ReserveRequest;
import com.delivery.promotion_service.dto.VoucherResponse;
import com.delivery.promotion_service.entity.Voucher;
import com.delivery.promotion_service.payload.BaseResponse;
import com.delivery.promotion_service.service.PromotionService;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;
    private final Validator validator;

    @Value("${app.internal.secret:}")
    private String internalSecret;

    @Value("${app.promotion.checkout-enabled:false}")
    private boolean checkoutEnabled;

    @PostMapping("/platform")
    public ResponseEntity<BaseResponse<VoucherResponse>> createPlatformVoucher(
            @RequestBody @Valid CreateVoucherRequest request,
            @RequestHeader(value = "X-Role", required = false) String role) {
        requireRole(role, "ADMIN");
        request.setCreatorType(Voucher.CreatorType.PLATFORM);
        request.setCreatorId(null);
        return ResponseEntity.ok(new BaseResponse<>(1,
                VoucherResponse.from(promotionService.createVoucher(request))));
    }

    @PostMapping("/collect/{code}")
    public ResponseEntity<BaseResponse<String>> collectVoucher(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @PathVariable String code) {
        requireRole(role, "USER");
        promotionService.collectVoucher(userId, code);
        return ResponseEntity.ok(new BaseResponse<>(1, "Collected successfully"));
    }

    @GetMapping("/my-vouchers")
    public ResponseEntity<BaseResponse<java.util.List<VoucherResponse>>> getMyVouchers(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Role", required = false) String role) {
        requireRole(role, "USER");
        return ResponseEntity.ok(new BaseResponse<>(1, toResponse(promotionService.getCollectedVouchers(userId))));
    }

    @GetMapping("/merchant")
    public ResponseEntity<BaseResponse<java.util.List<VoucherResponse>>> listMerchantVouchers(
            @RequestHeader("X-User-Id") Long merchantId,
            @RequestHeader(value = "X-Role", required = false) String role) {
        requireRole(role, "SHOP_OWNER");
        return ResponseEntity.ok(new BaseResponse<>(1, toResponse(promotionService.listMerchantVouchers(merchantId))));
    }

    @GetMapping("/admin")
    public ResponseEntity<BaseResponse<java.util.List<VoucherResponse>>> listAllVouchers(
            @RequestHeader(value = "X-Role", required = false) String role) {
        requireRole(role, "ADMIN");
        return ResponseEntity.ok(new BaseResponse<>(1, toResponse(promotionService.listAllVouchers())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> deleteVoucher(
            @PathVariable Long id,
            @RequestHeader(value = "X-Role", required = false) String role) {
        requireRole(role, "ADMIN");
        promotionService.deleteVoucher(id);
        return ResponseEntity.ok(new BaseResponse<>(1, null));
    }

    @PostMapping("/calculate")
    public ResponseEntity<BaseResponse<CalculateResponse>> calculate(
            @Valid @RequestBody CartContextRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Role", required = false) String role) {
        requireRole(role, "USER");
        if (!checkoutEnabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Voucher checkout is disabled until reservation recovery is proven");
        }
        request.setUserId(userId);
        return ResponseEntity.ok(new BaseResponse<>(1, promotionService.calculate(request)));
    }

    @PostMapping("/reserve")
    public ResponseEntity<BaseResponse<String>> reserve(
            @RequestBody ReserveRequest request,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {
        if (internalSecret == null || internalSecret.isBlank()
                || internalToken == null || !internalSecret.equals(internalToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        if (!checkoutEnabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Voucher checkout is disabled until reservation recovery is proven");
        }
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid voucher reservation request");
        }
        promotionService.reserveVouchers(request);
        return ResponseEntity.ok(new BaseResponse<>(1, "Reserved successfully"));
    }

    private void requireRole(String actualRole, String requiredRole) {
        if (!requiredRole.equals(actualRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }

    private java.util.List<VoucherResponse> toResponse(java.util.List<Voucher> vouchers) {
        return vouchers.stream().map(VoucherResponse::from).toList();
    }
}
