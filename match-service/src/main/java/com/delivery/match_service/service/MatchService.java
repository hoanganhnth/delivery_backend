package com.delivery.match_service.service;

import com.delivery.match_service.dto.request.FindNearbyShippersRequest;
import com.delivery.match_service.dto.response.NearbyShipperResponse;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * ✅ Interface cho Match Service với Non-blocking approach
 * Theo Backend Instructions: Service interface pattern
 */
public interface MatchService {

    /**
     * Tìm các shipper gần nhất từ Tracking Service (Non-blocking)
     * 
     * @param request Thông tin vị trí và bán kính tìm kiếm
     * @return Mono<List> các shipper gần nhất
     */
    Mono<List<NearbyShipperResponse>> findNearbyShippers(FindNearbyShippersRequest request, Long userId, String role);
    
    /**
     * Dừng quá trình matching cho một delivery cụ thể
     * 
     * @param deliveryId ID của delivery bị hủy
     * @param orderId ID của order bị hủy
     * @param reason Lý do hủy
     */
    void stopMatchingProcess(Long deliveryId, Long orderId, String reason);
}
