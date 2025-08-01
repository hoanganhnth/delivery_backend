package com.delivery.match_service.controller;

import com.delivery.match_service.common.constants.ApiPathConstants;
import com.delivery.match_service.common.constants.HttpHeaderConstants;
import com.delivery.match_service.dto.request.FindNearbyShippersRequest;
import com.delivery.match_service.dto.response.NearbyShipperResponse;
import com.delivery.match_service.payload.BaseResponse;
import com.delivery.match_service.service.MatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * ✅ Match Controller - Tìm shipper gần nhất với Non-blocking approach
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
     * ✅ Non-blocking API tìm shipper gần nhất với flexible content type support
     * 
     * POST /api/match/nearby-shippers
     * Request Body: {
     * "latitude": 10.762622,
     * "longitude": 106.660172,
     * "radiusKm": 5.0,
     * "maxShippers": 10
     * }
     * 
     * @param request Thông tin vị trí và bán kính tìm kiếm
     * @param userId  User ID từ header (optional)
     * @param role    Role từ header (optional)
     * @return Mono<ResponseEntity> với list shipper gần nhất
     */
    @PostMapping(value = ApiPathConstants.NEARBY_SHIPPERS, 
                consumes = {"application/json", "text/plain", "*/*"},
                produces = "application/json")
    public Mono<ResponseEntity<BaseResponse<List<NearbyShipperResponse>>>> findNearbyShippers(
            @RequestBody FindNearbyShippersRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long userId,
            @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        
        try {
            // ✅ Validate input với utility class
            String validationError = com.delivery.match_service.common.util.ValidationUtil
                    .validateFindNearbyShippersRequest(request);

            if (validationError != null) {
                return Mono.just(ResponseEntity.badRequest()
                        .body(new BaseResponse<>(0, null, validationError)));
            }

            // ✅ Non-blocking call service để lấy nearby shippers từ Tracking Service
            return matchService.findNearbyShippers(request, userId, role)
                    .map(nearbyShippers -> ResponseEntity.ok(new BaseResponse<>(1, nearbyShippers,
                            "Tìm thấy " + nearbyShippers.size() + " shipper gần vị trí yêu cầu")))
                    .onErrorReturn(ResponseEntity.status(500)
                            .body(new BaseResponse<>(0, null, "Lỗi khi tìm kiếm shipper gần nhất")));
            
        } catch (Exception parseEx) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(new BaseResponse<>(0, null, parseEx.getMessage())));
        }
    }
}
