package com.delivery.delivery_service.controller;

import com.delivery.delivery_service.common.constants.ApiPathConstants;
import com.delivery.delivery_service.dto.request.AcceptDeliveryRequest;
import com.delivery.delivery_service.dto.request.AcceptBatchRequest;
import com.delivery.delivery_service.dto.request.RejectBatchRequest;
import com.delivery.delivery_service.dto.request.CancelDeliveryAssignmentRequest;
import com.delivery.delivery_service.dto.response.DeliveryResponse;
import com.delivery.delivery_service.dto.response.DeliveryOfferResponse;
import com.delivery.delivery_service.entity.DeliveryStatus;
import com.delivery.delivery_service.payload.BaseResponse;
import com.delivery.delivery_service.service.DeliveryService;
import com.delivery.delivery_service.service.DeliveryBatchAcceptanceService;
import com.delivery.delivery_service.service.DeliveryBatchLifecycleService;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@RestController
@RequestMapping(ApiPathConstants.DELIVERIES)
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final DeliveryBatchAcceptanceService batchAcceptanceService;
    private final DeliveryBatchLifecycleService batchLifecycleService;
    private final boolean legacyCompatibility;

    @Autowired
    public DeliveryController(DeliveryService deliveryService, DeliveryBatchAcceptanceService batchAcceptanceService,
                              DeliveryBatchLifecycleService batchLifecycleService) {
        this.deliveryService = deliveryService;
        this.batchAcceptanceService = batchAcceptanceService;
        this.batchLifecycleService = batchLifecycleService;
        this.legacyCompatibility = false;
    }

    /** Compatibility constructor for legacy controller authorization tests. */
    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
        this.batchAcceptanceService = null;
        this.batchLifecycleService = null;
        this.legacyCompatibility = true;
    }

    @PostMapping("/batch/accept")
    public ResponseEntity<BaseResponse<DeliveryResponse>> acceptBatch(
            @Valid @RequestBody AcceptBatchRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        if (batchAcceptanceService == null) {
            throw new IllegalStateException("Batch acceptance support is unavailable");
        }
        DeliveryResponse response = batchAcceptanceService.accept(
                request, actor.getPrincipalId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Nhận batch thành công"));
    }

    @PostMapping("/batch/reject")
    public ResponseEntity<BaseResponse<Void>> rejectBatch(
            @Valid @RequestBody RejectBatchRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        if (batchLifecycleService == null) throw new IllegalStateException("Batch lifecycle support is unavailable");
        // The lifecycle service uses the same locked retirement path for explicit rejection.
        batchLifecycleService.reject(request.getBatchId(), actor.getPrincipalId(), getRoleString(actor), request.getReason());
        return ResponseEntity.ok(new BaseResponse<>(1, null, "Đã từ chối batch"));
    }

    @PostMapping("/accept")
    public ResponseEntity<BaseResponse<DeliveryResponse>> acceptDelivery(
            @Valid @RequestBody AcceptDeliveryRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        DeliveryResponse response = deliveryService.acceptDelivery(
                request, actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Nhận đơn hàng thành công"));
    }

    @PostMapping("/cancel-assignment")
    public ResponseEntity<BaseResponse<DeliveryResponse>> cancelAssignedDelivery(
            @Valid @RequestBody CancelDeliveryAssignmentRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        DeliveryResponse response = deliveryService.cancelAssignedDelivery(
                request.getOrderId(), actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor), request.getReason());
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Đã huỷ đơn, đang tìm shipper mới"));
    }

    @GetMapping("/offers/current")
    public ResponseEntity<BaseResponse<DeliveryOfferResponse>> getCurrentOffer(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        DeliveryOfferResponse response = legacyCompatibility
                ? deliveryService.getCurrentOffer(actor.getPrincipalId(), getRoleString(actor))
                : deliveryService.getCurrentOffer(actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response,
                response == null ? "Không có offer đang hoạt động" : "Lấy offer hiện tại thành công"));
    }

    @GetMapping("/offers/current-batch")
    public ResponseEntity<BaseResponse<com.delivery.delivery_service.dto.response.DeliveryBatchOfferResponse>> getCurrentBatchOffer(
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        var response = batchAcceptanceService == null ? null : batchAcceptanceService.currentOffer(
                actor.getPrincipalId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response,
                response == null ? "Không có batch offer đang hoạt động" : "Lấy batch offer thành công"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<DeliveryResponse>> getDelivery(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        DeliveryResponse response = deliveryService.getDeliveryById(
                id, actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy thông tin delivery thành công"));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<BaseResponse<DeliveryResponse>> updateStatus(
            @PathVariable Long id,
            @RequestParam DeliveryStatus status,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        DeliveryResponse response = deliveryService.updateDeliveryStatus(
                id, status, actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Cập nhật trạng thái delivery thành công"));
    }

    @GetMapping("/shipper/{shipperId}")
    public ResponseEntity<BaseResponse<List<DeliveryResponse>>> getDeliveriesByShipper(
            @PathVariable Long shipperId,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        List<DeliveryResponse> response = deliveryService.getDeliveriesByShipper(
                shipperId, actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy danh sách delivery của shipper thành công"));
    }

    @GetMapping("/shipper/{shipperId}/active")
    public ResponseEntity<BaseResponse<List<DeliveryResponse>>> getActiveDeliveriesByShipper(
            @PathVariable Long shipperId,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        List<DeliveryResponse> response = deliveryService.getActiveDeliveriesByShipper(
                shipperId, actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy danh sách delivery đang hoạt động thành công"));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<BaseResponse<DeliveryResponse>> getDeliveryByOrderId(
            @PathVariable Long orderId,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        DeliveryResponse response = deliveryService.getDeliveryByOrderId(
                orderId, actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy thông tin delivery theo order thành công"));
    }

    private void requireActor(AuthenticatedActor actor) {
        if (actor == null || actor.getPrincipalId() == null || actor.getLegacyUserId() == null) {
            throw new AccessDeniedException("Yêu cầu đăng nhập");
        }
    }

    private String getRoleString(AuthenticatedActor actor) {
        if (actor == null) return null;
        if (actor.isAdmin()) return "ADMIN";
        if (actor.isShipper()) return "SHIPPER";
        if (actor.isShopOwner()) return "SHOP_OWNER";
        return "USER";
    }
}
