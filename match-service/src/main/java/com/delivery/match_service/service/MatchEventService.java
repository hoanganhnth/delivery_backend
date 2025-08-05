package com.delivery.match_service.service;

import com.delivery.match_service.dto.event.FindShipperEvent;
import com.delivery.match_service.dto.response.NearbyShipperResponse;

import java.util.List;

/**
 * ✅ Match Event Service để handle publishing events theo Backend Instructions
 * Separate business logic from Kafka listener
 */
public interface MatchEventService {
    
    /**
     * ✅ Process found shippers và publish appropriate events
     * @param deliveryEvent Original FindShipperEvent
     * @param shippers List of nearby shippers found
     */
    void processShipperMatchResult(FindShipperEvent deliveryEvent, List<NearbyShipperResponse> shippers);
    
    /**
     * ✅ Publish MatchFoundEvent khi tìm thấy shipper phù hợp
     * @param deliveryEvent Original delivery event
     * @param selectedShipper Best shipper selected
     */
    void publishMatchFoundEvent(FindShipperEvent deliveryEvent, NearbyShipperResponse selectedShipper);
    
    /**
     * ✅ Publish NoShipperAvailableEvent khi không tìm thấy shipper nào
     * @param deliveryEvent Original delivery event
     */
    void publishNoShipperAvailableEvent(FindShipperEvent deliveryEvent);
    
    /**
     * ✅ Select best shipper from available list
     * @param shippers List of available shippers
     * @return Best shipper selected
     */
    NearbyShipperResponse selectBestShipper(List<NearbyShipperResponse> shippers);
}
