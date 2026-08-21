package com.delivery.delivery_service.controller;

import com.delivery.delivery_service.common.constants.ApiPathConstants;
import com.delivery.delivery_service.dto.request.AcceptDeliveryRequest;
import com.delivery.delivery_service.dto.request.CancelDeliveryAssignmentRequest;
import com.delivery.delivery_service.dto.response.DeliveryResponse;
import com.delivery.delivery_service.dto.response.DeliveryOfferResponse;
import com.delivery.delivery_service.entity.DeliveryStatus;
import com.delivery.delivery_service.payload.BaseResponse;
import com.delivery.delivery_service.service.DeliveryService;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping(ApiPathConstants.DELIVERIES)
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
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
        DeliveryOfferResponse response = deliveryService.getCurrentOffer(
                actor.getPrincipalId(), actor.getLegacyUserId(), getRoleString(actor));
        return ResponseEntity.ok(new BaseResponse<>(1, response,
                response == null ? "Không có offer đang hoạt động" : "Lấy offer hiện tại thành công"));
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
