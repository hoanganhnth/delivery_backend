package com.delivery.match_service.common.util;

import com.delivery.match_service.dto.request.FindNearbyShippersRequest;

/**
 * ✅ Validation Utility cho Match Service
 * Theo Backend Instructions: Common utilities trong common package
 */
public class ValidationUtil {
    
    /**
     * Validate FindNearbyShippersRequest
     * @param request Request cần validate
     * @return Error message nếu invalid, null nếu valid
     */
    public static String validateFindNearbyShippersRequest(FindNearbyShippersRequest request) {
        if (request == null) {
            return "Request không được null";
        }
        
        if (request.getLatitude() < -90 || request.getLatitude() > 90) {
            return "Latitude phải trong khoảng -90 đến 90";
        }
        
        if (request.getLongitude() < -180 || request.getLongitude() > 180) {
            return "Longitude phải trong khoảng -180 đến 180";
        }
        
        if (request.getRadiusKm() <= 0 || request.getRadiusKm() > 50) {
            return "Bán kính phải từ 0.1 đến 50 km";
        }
        
        if (request.getMaxShippers() <= 0 || request.getMaxShippers() > 100) {
            return "Số lượng shipper phải từ 1 đến 100";
        }
        
        return null; // Valid
    }
    
    private ValidationUtil() {
        // Utility class
    }
}
