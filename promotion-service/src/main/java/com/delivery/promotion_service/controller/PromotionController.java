package com.delivery.promotion_service.controller;

import com.delivery.promotion_service.dto.CalculateResponse;
import com.delivery.promotion_service.dto.BulkReserveRequest;
import com.delivery.promotion_service.dto.CartContextRequest;
import com.delivery.promotion_service.dto.CreateVoucherRequest;
import com.delivery.promotion_service.dto.ReserveRequest;
import com.delivery.promotion_service.dto.VoucherResponse;
import com.delivery.promotion_service.dto.VoucherReservationResponse;
import com.delivery.promotion_service.dto.PromotionReservationResponse;
import com.delivery.promotion_service.dto.VoucherCapabilityResponse;
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
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Value("${app.promotion.stacking-enabled:false}")
    private boolean stackingEnabled;

    @Value("${app.promotion.stacking-canary-principals:}")
    private String stackingCanaryPrincipals;

    @GetMapping("/capability")
    public ResponseEntity<BaseResponse<VoucherCapabilityResponse>> capability(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireRole(actor, "USER");
        Set<Long> allowlist = parsePrincipalAllowlist(stackingCanaryPrincipals);
        boolean enabled = checkoutEnabled && stackingEnabled && actor.getPrincipalId() != null
                && allowlist.contains(actor.getPrincipalId());
        return ResponseEntity.ok(new BaseResponse<>(1, new VoucherCapabilityResponse(
                enabled, 3,
                List.of("SHOP_DISCOUNT", "PLATFORM_DISCOUNT", "FREESHIP"),
                List.of("AUTO", "MANUAL"), true)));
    }

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

    @PostMapping("/shop")
    public ResponseEntity<BaseResponse<VoucherResponse>> createShopVoucher(
            @RequestBody @Valid CreateVoucherRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireRole(actor, "SHOP_OWNER");
        requirePrincipal(actor);
        return ResponseEntity.ok(new BaseResponse<>(1, VoucherResponse.from(
                promotionService.createShopVoucher(request, actor.getPrincipalId(), actor.getUserId()))));
    }

    @PostMapping("/collect/{code}")
    public ResponseEntity<BaseResponse<String>> collectVoucher(
            @PathVariable String code,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireRole(actor, "USER");
        if (actor.getPrincipalId() == null || java.util.Objects.equals(actor.getPrincipalId(), actor.getUserId())) {
            promotionService.collectVoucher(actor.getUserId(), code);
        } else {
            promotionService.collectVoucher(actor.getPrincipalId(), actor.getUserId(), code);
        }
        return ResponseEntity.ok(new BaseResponse<>(1, "Collected successfully"));
    }

    @GetMapping("/my-vouchers")
    public ResponseEntity<BaseResponse<List<VoucherResponse>>> getMyVouchers(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireRole(actor, "USER");
        List<Voucher> vouchers = actor.getPrincipalId() == null
                || java.util.Objects.equals(actor.getPrincipalId(), actor.getUserId())
                ? promotionService.getCollectedVouchers(actor.getUserId())
                : promotionService.getCollectedVouchers(actor.getPrincipalId(), actor.getUserId());
        return ResponseEntity.ok(new BaseResponse<>(1,
                toResponse(vouchers)));
    }

    @GetMapping("/merchant")
    public ResponseEntity<BaseResponse<List<VoucherResponse>>> listMerchantVouchers(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireRole(actor, "SHOP_OWNER");
        return ResponseEntity.ok(new BaseResponse<>(1, toResponse(promotionService.listMerchantVouchers(actor.getUserId()))));
    }

    @GetMapping("/shop")
    public ResponseEntity<BaseResponse<List<VoucherResponse>>> listShopVouchers(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireRole(actor, "SHOP_OWNER");
        requirePrincipal(actor);
        return ResponseEntity.ok(new BaseResponse<>(1, toResponse(
                promotionService.listShopVouchers(actor.getPrincipalId(), actor.getUserId()))));
    }

    @GetMapping("/admin")
    public ResponseEntity<BaseResponse<List<VoucherResponse>>> listAllVouchers(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireRole(actor, "ADMIN");
        return ResponseEntity.ok(new BaseResponse<>(1, toResponse(promotionService.listAllVouchers())));
    }

    @GetMapping("/admin/pending-shop")
    public ResponseEntity<BaseResponse<List<VoucherResponse>>> listPendingShopVouchers(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireRole(actor, "ADMIN");
        return ResponseEntity.ok(new BaseResponse<>(1, toResponse(promotionService.listPendingShopVouchers())));
    }

    @PostMapping("/admin/{id:[0-9]+}/approve")
    public ResponseEntity<BaseResponse<VoucherResponse>> approveShopVoucher(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireRole(actor, "ADMIN");
        requirePrincipal(actor);
        return ResponseEntity.ok(new BaseResponse<>(1, VoucherResponse.from(
                promotionService.approveShopVoucher(id, actor.getPrincipalId()))));
    }

    @PostMapping("/admin/{id:[0-9]+}/reject")
    public ResponseEntity<BaseResponse<VoucherResponse>> rejectShopVoucher(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireRole(actor, "ADMIN");
        requirePrincipal(actor);
        return ResponseEntity.ok(new BaseResponse<>(1, VoucherResponse.from(
                promotionService.rejectShopVoucher(id, actor.getPrincipalId(), reason))));
    }

    @PostMapping("/admin/{id:[0-9]+}/pause")
    public ResponseEntity<BaseResponse<VoucherResponse>> pauseVoucher(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireRole(actor, "ADMIN");
        return ResponseEntity.ok(new BaseResponse<>(1,
                VoucherResponse.from(promotionService.setVoucherActive(id, false))));
    }

    @PostMapping("/admin/{id:[0-9]+}/resume")
    public ResponseEntity<BaseResponse<VoucherResponse>> resumeVoucher(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireRole(actor, "ADMIN");
        return ResponseEntity.ok(new BaseResponse<>(1,
                VoucherResponse.from(promotionService.setVoucherActive(id, true))));
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

    /** Additive bulk reservation rail used by the stacked-voucher checkout. */
    @PostMapping("/internal/reservations")
    public ResponseEntity<BaseResponse<PromotionReservationResponse>> reserveBulk(
            @RequestBody @Valid BulkReserveRequest request,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {
        requireInternalCheckout(internalToken);
        return ResponseEntity.ok(new BaseResponse<>(1, promotionService.reserveVouchers(request)));
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

    @PostMapping("/internal/promotion-reservations/{reservationId}/commit")
    public ResponseEntity<BaseResponse<PromotionReservationResponse>> commitBulk(
            @PathVariable UUID reservationId,
            @RequestParam Long orderId,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {
        requireInternalCheckout(internalToken);
        return ResponseEntity.ok(new BaseResponse<>(1,
                promotionService.commitPromotionReservation(reservationId, orderId)));
    }

    @PostMapping("/internal/promotion-reservations/{reservationId}/release")
    public ResponseEntity<BaseResponse<PromotionReservationResponse>> releaseBulk(
            @PathVariable UUID reservationId,
            @RequestParam Long orderId,
            @RequestHeader(value = "Internal-Token", required = false) String internalToken) {
        requireInternalCheckout(internalToken);
        return ResponseEntity.ok(new BaseResponse<>(1,
                promotionService.releasePromotionReservation(reservationId, orderId)));
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

    private void requirePrincipal(AuthenticatedActor actor) {
        if (actor == null || actor.getPrincipalId() == null || actor.getPrincipalId() <= 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Stable principal is required");
        }
    }

    private Set<Long> parsePrincipalAllowlist(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(","))
                .map(String::trim).filter(item -> !item.isBlank())
                .map(item -> { try { return Long.valueOf(item); } catch (NumberFormatException ignored) { return null; } })
                .filter(item -> item != null && item > 0)
                .collect(Collectors.toUnmodifiableSet());
    }

    private List<VoucherResponse> toResponse(List<Voucher> vouchers) {
        return vouchers.stream().map(VoucherResponse::from).toList();
    }
}
