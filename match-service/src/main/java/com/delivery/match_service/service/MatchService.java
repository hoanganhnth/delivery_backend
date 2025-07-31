package com.delivery.match_service.service;

import com.delivery.match_service.dto.request.FindNearbyShippersRequest;
import com.delivery.match_service.dto.response.NearbyShipperResponse;

import java.util.List;

/**
 * ✅ Interface cho Match Service
 * Theo Backend Instructions: Service interface pattern
 */
public interface MatchService {
    
    /**
     * Tìm các shipper gần nhất từ Tracking Service
     * @param request Thông tin vị trí và bán kính tìm kiếm
     * @return List các shipper gần nhất
     */
    List<NearbyShipperResponse> findNearbyShippers(FindNearbyShippersRequest request);
}
