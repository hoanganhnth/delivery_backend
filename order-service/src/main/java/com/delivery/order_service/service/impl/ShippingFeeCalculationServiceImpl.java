package com.delivery.order_service.service.impl;

import com.delivery.order_service.exception.ValidationException;
import com.delivery.order_service.service.ShippingFeeCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * ✅ Implementation tính phí ship động theo khoảng cách
 * 
 * Pricing Formula (giống Shopee Food/Grab):
 * - Base Fee: 12,000 VNĐ (2km đầu tiên)
 * - Distance Fee: 4,500 VNĐ/km (từ km thứ 3 trở đi)
 * 
 * Example:
 * - Distance 1.5km: 12,000 VNĐ (base only)
 * - Distance 3km: 12,000 + (1km × 4,500) = 16,500 VNĐ
 * - Distance 5km: 12,000 + (3km × 4,500) = 25,500 VNĐ
 */
@Slf4j
@Service
public class ShippingFeeCalculationServiceImpl implements ShippingFeeCalculationService {
    
    // ✅ Pricing Constants (có thể move vào config sau)
    private static final BigDecimal BASE_FEE = new BigDecimal("12000"); // 12k cho 2km đầu
    private static final BigDecimal FREE_DISTANCE_KM = new BigDecimal("2"); // 2km miễn phí (base fee đã bao gồm)
    private static final BigDecimal DISTANCE_FEE_PER_KM = new BigDecimal("4500"); // 4.5k/km
    private static final BigDecimal MIN_SHIPPING_FEE = new BigDecimal("12000"); // Tối thiểu 12k
    private static final BigDecimal MAX_SHIPPING_FEE = new BigDecimal("50000"); // Tối đa 50k (giới hạn khoảng cách)
    
    @Override
    public BigDecimal calculateShippingFee(
            Double pickupLat, 
            Double pickupLng, 
            Double deliveryLat, 
            Double deliveryLng,
            BigDecimal subtotal) {
        
        requireVietnamCoordinates(pickupLat, pickupLng, deliveryLat, deliveryLng);

        // ✅ Calculate distance
        double distanceKm = calculateDistance(pickupLat, pickupLng, deliveryLat, deliveryLng);
        log.info("📏 Calculated distance: {} km", String.format("%.2f", distanceKm));
            
        // ✅ Calculate shipping fee based on distance
        BigDecimal shippingFee = BASE_FEE; // Start with base fee
            
        if (distanceKm > FREE_DISTANCE_KM.doubleValue()) {
            // Tính thêm phí cho khoảng cách vượt quá 2km
            double extraDistanceKm = distanceKm - FREE_DISTANCE_KM.doubleValue();
            BigDecimal extraFee = DISTANCE_FEE_PER_KM
                .multiply(BigDecimal.valueOf(extraDistanceKm))
                .setScale(0, RoundingMode.UP); // Làm tròn lên
                
            shippingFee = shippingFee.add(extraFee);
                
            log.info("💰 Extra distance: {} km × {} VNĐ/km = {} VNĐ",
                String.format("%.2f", extraDistanceKm),
                DISTANCE_FEE_PER_KM,
                extraFee);
        }
            
        // ✅ Apply min/max limits
        if (shippingFee.compareTo(MIN_SHIPPING_FEE) < 0) {
            shippingFee = MIN_SHIPPING_FEE;
        }
        if (shippingFee.compareTo(MAX_SHIPPING_FEE) > 0) {
            log.warn("⚠️ Shipping fee {} exceeds max, capping at {}", shippingFee, MAX_SHIPPING_FEE);
            shippingFee = MAX_SHIPPING_FEE;
        }
            
        // Round to nearest 500 VNĐ (giống Grab/Shopee)
        shippingFee = roundToNearest500(shippingFee);
            
        log.info("✅ Final shipping fee: {} VNĐ for {} km", shippingFee, String.format("%.2f", distanceKm));
            
        return shippingFee;
    }
    
    @Override
    public double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        requireVietnamCoordinates(lat1, lng1, lat2, lng2);
        // ✅ Haversine formula để tính khoảng cách giữa 2 tọa độ
        final double EARTH_RADIUS_KM = 6371;
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_KM * c;
    }
    
    private void requireVietnamCoordinates(Double pickupLat, Double pickupLng,
            Double deliveryLat, Double deliveryLng) {
        if (!isFiniteInRange(pickupLat, 8.0, 24.0)
                || !isFiniteInRange(deliveryLat, 8.0, 24.0)
                || !isFiniteInRange(pickupLng, 102.0, 110.0)
                || !isFiniteInRange(deliveryLng, 102.0, 110.0)) {
            throw new ValidationException(
                    "Tọa độ lấy/giao hàng là bắt buộc và phải nằm trong phạm vi Việt Nam");
        }
    }

    private boolean isFiniteInRange(Double value, double min, double max) {
        return value != null && Double.isFinite(value) && value >= min && value <= max;
    }
    
    /**
     * Làm tròn đến 500 VNĐ gần nhất (giống Grab/Shopee)
     */
    private BigDecimal roundToNearest500(BigDecimal amount) {
        BigDecimal divisor = new BigDecimal("500");
        return amount.divide(divisor, 0, RoundingMode.HALF_UP)
                     .multiply(divisor);
    }
}
