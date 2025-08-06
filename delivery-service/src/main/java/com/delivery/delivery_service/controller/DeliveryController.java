package com.delivery.delivery_service.controller;

import com.delivery.delivery_service.common.constants.ApiPathConstants;
import com.delivery.delivery_service.common.constants.HttpHeaderConstants;
import com.delivery.delivery_service.dto.request.AcceptDeliveryRequest;
import com.delivery.delivery_service.dto.request.AssignDeliveryRequest;
import com.delivery.delivery_service.dto.response.DeliveryResponse;
import com.delivery.delivery_service.dto.response.DeliveryTrackingResponse;
import com.delivery.delivery_service.entity.DeliveryStatus;
import com.delivery.delivery_service.payload.BaseResponse;
import com.delivery.delivery_service.service.DeliveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(ApiPathConstants.DELIVERIES)
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    /**
     * POST /findNearbyShippers- Tìm shipper gần nhất
     */
    // @PostMapping("/findNearbyShippers")
    // public ResponseEntity<BaseResponse<List<Long>>> findNearbyShippers(
    //         @RequestBody AssignDeliveryRequest request,
    //         @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
    //         @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
    //     List<Long> shipperIds = deliveryService.findNearbyShippers(request, userId, role);
    //     return ResponseEntity.ok(new BaseResponse<>(1, shipperIds, "Tìm shipper gần nhất thành công"));
    // }

    /**
     * POST /assign - Giao shipper với đơn hàng
     */
    @PostMapping("/assign")
    public ResponseEntity<BaseResponse<DeliveryResponse>> assignDelivery(
            @RequestBody AssignDeliveryRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        DeliveryResponse response = deliveryService.assignDelivery(request, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Phân công giao hàng thành công"));
    }
    
    /**
     * ✅ POST /accept - Shipper nhận đơn hàng
     */
    @PostMapping("/accept")
    public ResponseEntity<BaseResponse<DeliveryResponse>> acceptDelivery(
            @RequestBody AcceptDeliveryRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long shipperId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE) String role) {
        DeliveryResponse response = deliveryService.acceptDelivery(request, shipperId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Nhận đơn hàng thành công"));
    }

    /**
     * GET /delivery/:id/track - Lấy trạng thái đơn, vị trí shipper
     */
    @GetMapping("/{id}/track")
    public ResponseEntity<BaseResponse<DeliveryTrackingResponse>> trackDelivery(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        DeliveryTrackingResponse response = deliveryService.getDeliveryTracking(id, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy thông tin tracking thành công"));
    }

    /**
     * GET /:id - Lấy thông tin delivery
     */
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<DeliveryResponse>> getDelivery(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        DeliveryResponse response = deliveryService.getDeliveryById(id, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy thông tin delivery thành công"));
    }

    /**
     * PUT /:id/location - Cập nhật vị trí shipper
     */
    // @PutMapping("/{id}/location")
    // public ResponseEntity<BaseResponse<DeliveryResponse>> updateLocation(
    //         @PathVariable Long id,
    //         @RequestBody UpdateLocationRequest request,
    //         @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
    //         @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
    //     DeliveryResponse response = deliveryService.updateShipperLocation(id, request, userId, role);
    //     return ResponseEntity.ok(new BaseResponse<>(1, response, "Cập nhật vị trí shipper thành công"));
    // }

    /**
     * PUT /:id/status - Cập nhật trạng thái delivery
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<BaseResponse<DeliveryResponse>> updateStatus(
            @PathVariable Long id,
            @RequestParam DeliveryStatus status,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        DeliveryResponse response = deliveryService.updateDeliveryStatus(id, status, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Cập nhật trạng thái delivery thành công"));
    }

    /**
     * GET /shipper/:shipperId - Lấy danh sách delivery của shipper
     */
    @GetMapping("/shipper/{shipperId}")
    public ResponseEntity<BaseResponse<List<DeliveryResponse>>> getDeliveriesByShipper(
            @PathVariable Long shipperId,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        List<DeliveryResponse> response = deliveryService.getDeliveriesByShipper(shipperId, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy danh sách delivery của shipper thành công"));
    }

    /**
     * GET /shipper/:shipperId/active - Lấy các delivery đang active của shipper
     */
    @GetMapping("/shipper/{shipperId}/active")
    public ResponseEntity<BaseResponse<List<DeliveryResponse>>> getActiveDeliveriesByShipper(
            @PathVariable Long shipperId,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        List<DeliveryResponse> response = deliveryService.getActiveDeliveriesByShipper(shipperId, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy danh sách delivery đang hoạt động thành công"));
    }

    /**
     * GET /order/:orderId - Lấy delivery theo order ID
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<BaseResponse<DeliveryResponse>> getDeliveryByOrderId(
            @PathVariable Long orderId,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        DeliveryResponse response = deliveryService.getDeliveryByOrderId(orderId, userId, role);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy thông tin delivery theo order thành công"));
    }
}
