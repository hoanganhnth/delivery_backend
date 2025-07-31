package com.delivery.match_service.controller;

import com.delivery.match_service.common.constants.ApiPathConstants;
import com.delivery.match_service.common.constants.HttpHeaderConstants;
import com.delivery.match_service.dto.request.FindNearbyShippersRequest;
import com.delivery.match_service.dto.response.NearbyShipperResponse;
import com.delivery.match_service.payload.BaseResponse;
import com.delivery.match_service.service.MatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ✅ Match Controller - Tìm shipper gần nhất
 * Theo Backend Instructions: Controller pattern với BaseResponse wrapper
 */
@RestController
@RequestMapping(ApiPathConstants.MATCH)
public class MatchController {
    
    private final MatchService matchService;
    
    /**
     * ✅ Constructor Injection thay vì @Autowired field injection
     * Theo Backend Instructions: Constructor injection là REQUIRED
     */
    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }
    
    /**
     * ✅ API tìm shipper gần nhất
     * 
     * POST /api/match/nearby-shippers
     * Request Body: {
     *   "latitude": 10.762622,
     *   "longitude": 106.660172,
     *   "radiusKm": 5.0,
     *   "maxShippers": 10
     * }
     * 
     * @param request Thông tin vị trí và bán kính tìm kiếm
     * @param userId User ID từ header (optional)
     * @param role Role từ header (optional)
     * @return List shipper gần nhất với khoảng cách
     */
    @PostMapping(ApiPathConstants.NEARBY_SHIPPERS)
    public ResponseEntity<BaseResponse<List<NearbyShipperResponse>>> findNearbyShippers(
            @RequestBody FindNearbyShippersRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        
        // ✅ Validate input
        if (request.getLatitude() < -90 || request.getLatitude() > 90) {
            return ResponseEntity.badRequest()
                    .body(new BaseResponse<>(0, null, "Latitude phải trong khoảng -90 đến 90"));
        }
        
        if (request.getLongitude() < -180 || request.getLongitude() > 180) {
            return ResponseEntity.badRequest()
                    .body(new BaseResponse<>(0, null, "Longitude phải trong khoảng -180 đến 180"));
        }
        
        if (request.getRadiusKm() <= 0 || request.getRadiusKm() > 50) {
            return ResponseEntity.badRequest()
                    .body(new BaseResponse<>(0, null, "Bán kính phải từ 0.1 đến 50 km"));
        }
        
        if (request.getMaxShippers() <= 0 || request.getMaxShippers() > 100) {
            return ResponseEntity.badRequest()
                    .body(new BaseResponse<>(0, null, "Số lượng shipper phải từ 1 đến 100"));
        }
        
        // ✅ Call service để lấy nearby shippers từ Tracking Service
        List<NearbyShipperResponse> nearbyShippers = matchService.findNearbyShippers(request);
        
        // ✅ Return với BaseResponse wrapper theo Backend Instructions
        return ResponseEntity.ok(new BaseResponse<>(1, nearbyShippers, 
                "Tìm thấy " + nearbyShippers.size() + " shipper gần vị trí yêu cầu"));
    }
}
