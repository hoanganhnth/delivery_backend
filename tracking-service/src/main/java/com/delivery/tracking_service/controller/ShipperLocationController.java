package com.delivery.tracking_service.controller;

import com.delivery.tracking_service.common.constants.ApiPathConstants;
import com.delivery.tracking_service.common.constants.HttpHeaderConstants;
import com.delivery.tracking_service.common.constants.RoleConstants;
import com.delivery.tracking_service.dto.request.UpdateLocationRequest;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.payload.BaseResponse;
import com.delivery.tracking_service.service.ShipperLocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(ApiPathConstants.SHIPPER_LOCATIONS)
@RequiredArgsConstructor
public class ShipperLocationController {

    private final ShipperLocationService shipperLocationService;

    @PostMapping("/update")
    public ResponseEntity<BaseResponse<ShipperLocationResponse>> updateLocation(
            @RequestHeader(HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(HttpHeaderConstants.X_ROLE) String role,
            @Valid @RequestBody UpdateLocationRequest request) {
        
        // Only shippers can update their own location
        if (!RoleConstants.SHIPPER.equals(role)) {
            return ResponseEntity.status(403)
                .body(new BaseResponse<>(0, null, "Không có quyền truy cập"));
        }
        
        ShipperLocationResponse response = shipperLocationService.updateLocation(userId, request);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Cập nhật vị trí thành công"));
    }

    @GetMapping("/{shipperId}")
    public ResponseEntity<BaseResponse<ShipperLocationResponse>> getShipperLocation(
            @PathVariable Long shipperId) {
        
        return shipperLocationService.getShipperLocation(shipperId)
            .map(location -> ResponseEntity.ok(new BaseResponse<>(1, location, "Lấy vị trí thành công")))
            .orElse(ResponseEntity.ok(new BaseResponse<>(0, null, "Không tìm thấy vị trí shipper")));
    }

    @GetMapping("/online")
    public ResponseEntity<BaseResponse<List<ShipperLocationResponse>>> getOnlineShippers() {
        List<ShipperLocationResponse> onlineShippers = shipperLocationService.getOnlineShippers();
        return ResponseEntity.ok(new BaseResponse<>(1, onlineShippers, 
            "Lấy danh sách shipper online thành công"));
    }

    /**
     * ✅ NEW: Tìm shippers trong bán kính sử dụng Redis GEO theo Backend Instructions
     */
    @GetMapping("/nearby")
    public ResponseEntity<BaseResponse<List<ShipperLocationResponse>>> findNearbyShippers(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(defaultValue = "5.0") Double radiusKm,
            @RequestParam(defaultValue = "10") Integer limit) {
        
        // Validation input theo Backend Instructions
        if (lat == null || lng == null) {
            return ResponseEntity.badRequest()
                    .body(new BaseResponse<>(0, null, "Latitude và longitude không được để trống"));
        }
        
        if (lat < -90 || lat > 90) {
            return ResponseEntity.badRequest()
                    .body(new BaseResponse<>(0, null, "Latitude phải từ -90 đến 90"));
        }
        
        if (lng < -180 || lng > 180) {
            return ResponseEntity.badRequest()
                    .body(new BaseResponse<>(0, null, "Longitude phải từ -180 đến 180"));
        }
        
        if (radiusKm <= 0 || radiusKm > 50) {
            return ResponseEntity.badRequest()
                    .body(new BaseResponse<>(0, null, "Bán kính phải từ 0.1 đến 50km"));
        }
        
        // ✅ Use Redis GEO spatial query
        List<ShipperLocationResponse> nearbyShippers = shipperLocationService.findNearbyShippers(
            lat, lng, radiusKm, limit
        );
        
        String message = String.format("Tìm thấy %d shippers trong bán kính %.1fkm [Redis GEO]", 
                                      nearbyShippers.size(), radiusKm);
        
        return ResponseEntity.ok(new BaseResponse<>(1, nearbyShippers, message));
    }
    
    /**
     * ✅ NEW: Tính khoảng cách giữa 2 shippers sử dụng Redis GEODIST
     */
    @GetMapping("/distance")
    public ResponseEntity<BaseResponse<Double>> getDistanceBetweenShippers(
            @RequestParam Long shipperId1,
            @RequestParam Long shipperId2) {
        
        if (shipperId1.equals(shipperId2)) {
            return ResponseEntity.badRequest()
                    .body(new BaseResponse<>(0, null, "ShipperID phải khác nhau"));
        }
        
        Double distance = shipperLocationService.getDistanceBetweenShippers(shipperId1, shipperId2);
        
        if (distance != null) {
            String message = String.format("Khoảng cách giữa shipper %d và %d: %.2fkm [Redis GEODIST]", 
                                          shipperId1, shipperId2, distance);
            return ResponseEntity.ok(new BaseResponse<>(1, distance, message));
        } else {
            return ResponseEntity.ok(new BaseResponse<>(0, null, 
                "Không thể tính khoảng cách (một trong 2 shipper không có vị trí)"));
        }
    }

    @PostMapping("/offline")
    public ResponseEntity<BaseResponse<String>> markOffline(
            @RequestHeader(HttpHeaderConstants.X_USER_ID) Long userId,
            @RequestHeader(HttpHeaderConstants.X_ROLE) String role) {
        
        // Only shippers can mark themselves offline
        if (!RoleConstants.SHIPPER.equals(role)) {
            return ResponseEntity.status(403)
                .body(new BaseResponse<>(0, null, "Không có quyền truy cập"));
        }
        
        shipperLocationService.markShipperOffline(userId);
        return ResponseEntity.ok(new BaseResponse<>(1, "Đã đánh dấu offline thành công"));
    }

    /**
     * ✅ Internal API: Đánh dấu shipper đang bận (gọi bởi delivery-service)
     */
    @PutMapping("/{shipperId}/busy")
    public ResponseEntity<BaseResponse<String>> markShipperBusy(
            @PathVariable Long shipperId) {
        shipperLocationService.markShipperBusy(shipperId);
        return ResponseEntity.ok(new BaseResponse<>(1, "Shipper " + shipperId + " marked as BUSY"));
    }

    /**
     * ✅ Internal API: Đánh dấu shipper rảnh (gọi bởi delivery-service)
     */
    @DeleteMapping("/{shipperId}/busy")
    public ResponseEntity<BaseResponse<String>> markShipperAvailable(
            @PathVariable Long shipperId) {
        shipperLocationService.markShipperAvailable(shipperId);
        return ResponseEntity.ok(new BaseResponse<>(1, "Shipper " + shipperId + " marked as AVAILABLE"));
    }
}
