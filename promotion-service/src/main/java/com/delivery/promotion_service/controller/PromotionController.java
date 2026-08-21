package com.delivery.promotion_service.controller;

import com.delivery.promotion_service.dto.CalculateResponse;
import com.delivery.promotion_service.dto.CartContextRequest;
import com.delivery.promotion_service.dto.CreateVoucherRequest;
import com.delivery.promotion_service.dto.ReserveRequest;
import com.delivery.promotion_service.dto.VoucherResponse;
import com.delivery.promotion_service.dto.VoucherReservationResponse;
import com.delivery.promotion_service.entity.Voucher;
import com.delivery.promotion_service.payload.BaseResponse;
import com.delivery.promotion_service.service.PromotionService;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;

import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

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
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireRole(actor, "ADMIN");
        request.setCreatorType(Voucher.CreatorType.PLATFORM);
        request.setCreatorId(null);
        return ResponseEntity.ok(new BaseResponse<>(1,
                VoucherResponse.from(promotionService.createVoucher(request))));
    }

    @PostMapping("/collect/{code}")
    public ResponseEntity<BaseResponse<String>> collectVoucher(
            @PathVariable String code,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireRole(actor, "USER");
        promotionService.collectVoucher(actor.getPrincipalId(), actor.getUserId(), code);
        return ResponseEntity.ok(new BaseResponse<>(1, "Collected successfully"));
    }

    @GetMapping("/my-vouchers")
    public ResponseEntity<BaseResponse<List<VoucherResponse>>> getMyVouchers(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireRole(actor, "USER");
        return ResponseEntity.ok(new BaseResponse<>(1,
                toResponse(promotionService.getCollectedVouchers(actor.getPrincipalId(), actor.getUserId()))));
    }

    @GetMapping("/merchant")
    public ResponseEntity<BaseResponse<List<VoucherResponse>>> listMerchantVouchers(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireRole(actor, "SHOP_OWNER");
        return ResponseEntity.ok(new BaseResponse<>(1, toResponse(promotionService.listMerchantVouchers(actor.getUserId()))));
    }

    @GetMapping("/admin")
    public ResponseEntity<BaseResponse<List<VoucherResponse>>> listAllVouchers(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireRole(actor, "ADMIN");
        return ResponseEntity.ok(new BaseResponse<>(1, toResponse(promotionService.listAllVouchers())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> deleteVoucher(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireRole(actor, "ADMIN");
        promotionService.deleteVoucher(id);
        return ResponseEntity.ok(new BaseResponse<>(1, null));
    }

    @PostMapping("/internal/calculate")
    public ResponseEntity<BaseResponse<CalculateResponse>> calculate(
            @RequestBody CartContextRequest request,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {
        requireInternalCheckout(internalToken);
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid voucher quote request");
        }

        return ResponseEntity.ok(new BaseResponse<>(1, promotionService.calculate(request)));
    }

    @PostMapping("/internal/reserve")
    public ResponseEntity<BaseResponse<VoucherReservationResponse>> reserve(
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
        return ResponseEntity.ok(new BaseResponse<>(1, promotionService.reserveVoucher(request)));
    }

    @PostMapping("/internal/reservations/{reservationId}/commit")
    public ResponseEntity<BaseResponse<VoucherReservationResponse>> commit(
            @PathVariable UUID reservationId,
            @RequestParam Long orderId,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {
        requireInternalCheckout(internalToken);
        return ResponseEntity.ok(new BaseResponse<>(1,
                promotionService.commitReservation(reservationId, orderId)));
    }

    @PostMapping("/internal/reservations/{reservationId}/release")
    public ResponseEntity<BaseResponse<VoucherReservationResponse>> release(
            @PathVariable UUID reservationId,
            @RequestParam Long orderId,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {
        requireInternalCheckout(internalToken);
        return ResponseEntity.ok(new BaseResponse<>(1,
                promotionService.releaseReservation(reservationId, orderId)));
    }

    private void requireInternalCheckout(String internalToken) {
        if (internalSecret == null || internalSecret.isBlank()
                || internalToken == null || !internalSecret.equals(internalToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        if (!checkoutEnabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Voucher checkout is disabled until reservation recovery is proven");
        }
    }

    private void requireRole(AuthenticatedActor actor, String requiredRole) {
        if (actor == null || !actor.hasRole(requiredRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }

    private List<VoucherResponse> toResponse(List<Voucher> vouchers) {
        return vouchers.stream().map(VoucherResponse::from).toList();
    }
}
