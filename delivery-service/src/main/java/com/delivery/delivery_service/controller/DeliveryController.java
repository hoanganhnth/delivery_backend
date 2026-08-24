package com.delivery.delivery_service.controller;

import com.delivery.delivery_service.common.constants.ApiPathConstants;
import com.delivery.delivery_service.dto.request.AcceptDeliveryRequest;
import com.delivery.delivery_service.dto.request.AcceptBatchRequest;
import com.delivery.delivery_service.dto.request.RejectBatchRequest;
import com.delivery.delivery_service.dto.request.CancelDeliveryAssignmentRequest;
import com.delivery.delivery_service.dto.request.CreateProofUploadIntentRequest;
import com.delivery.delivery_service.dto.request.ReportDeliveryFailureRequest;
import com.delivery.delivery_service.dto.response.DeliveryResponse;
import com.delivery.delivery_service.dto.response.DeliveryOfferResponse;
import com.delivery.delivery_service.dto.response.ProofAccessResponse;
import com.delivery.delivery_service.dto.response.ProofOfDeliveryResponse;
import com.delivery.delivery_service.dto.response.ProofUploadIntentResponse;
import com.delivery.delivery_service.dto.response.DeliveryExceptionResponse;
import com.delivery.delivery_service.entity.DeliveryStatus;
import com.delivery.delivery_service.payload.BaseResponse;
import com.delivery.delivery_service.service.DeliveryService;
import com.delivery.delivery_service.service.DeliveryBatchAcceptanceService;
import com.delivery.delivery_service.service.DeliveryBatchLifecycleService;
import com.delivery.delivery_service.service.DeliveryProofOfDeliveryService;
import com.delivery.delivery_service.service.DeliveryExceptionService;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPathConstants.DELIVERIES)
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final DeliveryBatchAcceptanceService batchAcceptanceService;
    private final DeliveryBatchLifecycleService batchLifecycleService;
    private final com.delivery.delivery_service.service.ShipperIdentityResolver shipperIdentityResolver;
    private final com.delivery.delivery_service.service.DeliveryBatchSnapshotService batchSnapshotService;
    private final DeliveryProofOfDeliveryService proofOfDeliveryService;
    private final DeliveryExceptionService deliveryExceptionService;
    private final boolean legacyCompatibility;

    @Autowired
    public DeliveryController(DeliveryService deliveryService, DeliveryBatchAcceptanceService batchAcceptanceService,
                              DeliveryBatchLifecycleService batchLifecycleService,
                              com.delivery.delivery_service.service.ShipperIdentityResolver shipperIdentityResolver,
                              com.delivery.delivery_service.service.DeliveryBatchSnapshotService batchSnapshotService,
                              DeliveryProofOfDeliveryService proofOfDeliveryService,
                              DeliveryExceptionService deliveryExceptionService) {
        this.deliveryService = deliveryService;
        this.batchAcceptanceService = batchAcceptanceService;
        this.batchLifecycleService = batchLifecycleService;
        this.shipperIdentityResolver = shipperIdentityResolver;
        this.batchSnapshotService = batchSnapshotService;
        this.proofOfDeliveryService = proofOfDeliveryService;
        this.deliveryExceptionService = deliveryExceptionService;
        this.legacyCompatibility = false;
    }

    /** Compatibility constructor for batch controller fixtures. */
    public DeliveryController(DeliveryService deliveryService, DeliveryBatchAcceptanceService batchAcceptanceService,
                              DeliveryBatchLifecycleService batchLifecycleService,
                              com.delivery.delivery_service.service.ShipperIdentityResolver shipperIdentityResolver,
                              com.delivery.delivery_service.service.DeliveryBatchSnapshotService batchSnapshotService) {
        this(deliveryService, batchAcceptanceService, batchLifecycleService, shipperIdentityResolver,
                batchSnapshotService, null, null);
    }

    /** Compatibility constructor for existing controller fixtures. */
    public DeliveryController(DeliveryService deliveryService, DeliveryBatchAcceptanceService batchAcceptanceService,
                              DeliveryBatchLifecycleService batchLifecycleService) {
        this(deliveryService, batchAcceptanceService, batchLifecycleService, null, null, null, null);
    }

    /** Compatibility constructor for legacy controller authorization tests. */
    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
        this.batchAcceptanceService = null;
        this.batchLifecycleService = null;
        this.shipperIdentityResolver = null;
        this.batchSnapshotService = null;
        this.proofOfDeliveryService = null;
        this.deliveryExceptionService = null;
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
        DeliveryResponse response = shipperIdentityResolver == null
                ? batchAcceptanceService.accept(request, actor.getPrincipalId(), getRoleString(actor))
                : batchAcceptanceService.accept(request, actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Nhận batch thành công"));
    }

    @PostMapping("/batch/reject")
    public ResponseEntity<BaseResponse<Void>> rejectBatch(
            @Valid @RequestBody RejectBatchRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        if (batchLifecycleService == null) throw new IllegalStateException("Batch lifecycle support is unavailable");
        // The lifecycle service uses the same locked retirement path for explicit rejection.
        if (shipperIdentityResolver == null) {
            batchLifecycleService.reject(request.getBatchId(), actor.getPrincipalId(), getRoleString(actor), request.getReason());
        } else {
            batchLifecycleService.reject(request.getBatchId(), actor.getPrincipalId(), actor.getLegacyUserId(),
                    getRoleString(actor), request.getReason());
        }
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
        var response = batchAcceptanceService == null ? null
                : shipperIdentityResolver == null
                ? batchAcceptanceService.currentOffer(actor.getPrincipalId(), getRoleString(actor))
                : batchAcceptanceService.currentOffer(actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response,
                response == null ? "Không có batch offer đang hoạt động" : "Lấy batch offer thành công"));
    }

    /** Protected durable batch recovery; single-order routes remain unchanged. */
    @GetMapping("/batches/{batchId}")
    public ResponseEntity<BaseResponse<com.delivery.delivery_service.dto.response.DeliveryBatchSnapshotResponse>> getBatchSnapshot(
            @PathVariable UUID batchId,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        if (batchSnapshotService == null) {
            throw new IllegalStateException("Batch snapshot support is unavailable");
        }
        var response = batchSnapshotService.getSnapshot(batchId, actor.getPrincipalId(), actor.getLegacyUserId(),
                getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy snapshot batch thành công"));
    }

    @PostMapping("/{deliveryId}/proofs/upload-intent")
    public ResponseEntity<BaseResponse<ProofUploadIntentResponse>> createProofUploadIntent(
            @PathVariable Long deliveryId,
            @Valid @RequestBody CreateProofUploadIntentRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        requireProofService();
        ProofUploadIntentResponse response = proofOfDeliveryService.createUploadIntent(deliveryId, request,
                actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Đã tạo URL tải bằng chứng riêng tư"));
    }

    @PostMapping("/{deliveryId}/proofs/{proofId}/confirm")
    public ResponseEntity<BaseResponse<ProofOfDeliveryResponse>> confirmProofUpload(
            @PathVariable Long deliveryId,
            @PathVariable UUID proofId,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        requireProofService();
        ProofOfDeliveryResponse response = proofOfDeliveryService.confirmUpload(deliveryId, proofId,
                actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Đã xác nhận bằng chứng giao hàng"));
    }

    @GetMapping("/{deliveryId}/proofs/{proofId}/access")
    public ResponseEntity<BaseResponse<ProofAccessResponse>> createProofReadAccess(
            @PathVariable Long deliveryId,
            @PathVariable UUID proofId,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        requireProofService();
        ProofAccessResponse response = proofOfDeliveryService.createReadAccess(deliveryId, proofId,
                actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Đã tạo URL xem bằng chứng riêng tư"));
    }

    @PostMapping("/{deliveryId}/exceptions/failed")
    public ResponseEntity<BaseResponse<DeliveryExceptionResponse>> reportDeliveryFailure(
            @PathVariable Long deliveryId,
            @Valid @RequestBody ReportDeliveryFailureRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        requireExceptionService();
        DeliveryExceptionResponse response = deliveryExceptionService.reportFailure(deliveryId, request.getReason(),
                actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Đã ghi nhận sự cố giao hàng"));
    }

    @PostMapping("/{deliveryId}/exceptions/retry")
    public ResponseEntity<BaseResponse<DeliveryExceptionResponse>> useDeliveryRetry(
            @PathVariable Long deliveryId,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        requireExceptionService();
        DeliveryExceptionResponse response = deliveryExceptionService.useRetry(deliveryId,
                actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Đã dùng lượt giao lại"));
    }

    @PostMapping("/{deliveryId}/exceptions/return/confirm")
    public ResponseEntity<BaseResponse<DeliveryExceptionResponse>> confirmDeliveryReturn(
            @PathVariable Long deliveryId,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        requireExceptionService();
        DeliveryExceptionResponse response = deliveryExceptionService.confirmReturn(deliveryId,
                actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Nhà hàng đã xác nhận hoàn hàng"));
    }

    @GetMapping("/{deliveryId}/exception")
    public ResponseEntity<BaseResponse<DeliveryExceptionResponse>> getDeliveryException(
            @PathVariable Long deliveryId,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        requireActor(actor);
        requireExceptionService();
        DeliveryExceptionResponse response = deliveryExceptionService.getException(deliveryId,
                actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy sự cố giao hàng thành công"));
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

    private void requireProofService() {
        if (proofOfDeliveryService == null) {
            throw new IllegalStateException("Proof-of-delivery support is unavailable");
        }
    }

    private void requireExceptionService() {
        if (deliveryExceptionService == null) {
            throw new IllegalStateException("Delivery exception support is unavailable");
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
