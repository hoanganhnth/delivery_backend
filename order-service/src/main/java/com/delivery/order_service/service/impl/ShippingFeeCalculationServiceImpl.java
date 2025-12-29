package com.delivery.order_service.service.impl;

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
 * - Platform Commission: 20% (platform giữ, shipper nhận 80%)
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
    
    // Platform commission (20% - platform giữ, 80% cho shipper)
    private static final BigDecimal PLATFORM_COMMISSION_RATE = new BigDecimal("0.20");
    private static final BigDecimal SHIPPER_EARNING_RATE = new BigDecimal("0.80");
    
    @Override
    public BigDecimal calculateShippingFee(
            Double pickupLat, 
            Double pickupLng, 
            Double deliveryLat, 
            Double deliveryLng,
            BigDecimal subtotal) {
        
        try {
            // ✅ Validate coordinates
            if (pickupLat == null || pickupLng == null || deliveryLat == null || deliveryLng == null) {
                log.warn("⚠️ Missing coordinates, using minimum shipping fee: {}", MIN_SHIPPING_FEE);
                return MIN_SHIPPING_FEE;
            }
            
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
            
        } catch (Exception e) {
            log.error("💥 Error calculating shipping fee: {}", e.getMessage(), e);
            return MIN_SHIPPING_FEE;
        }
    }
    
    @Override
    public double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
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
    
    @Override
    public BigDecimal estimateShipperEarnings(BigDecimal shippingFee) {
        if (shippingFee == null) {
            return BigDecimal.ZERO;
        }
        
        // Shipper nhận 80% của phí ship (platform giữ 20%)
        BigDecimal shipperEarnings = shippingFee
            .multiply(SHIPPER_EARNING_RATE)
            .setScale(0, RoundingMode.DOWN); // Làm tròn xuống
        
        log.info("💰 Shipping fee: {} VNĐ → Shipper earnings: {} VNĐ ({}%)", 
            shippingFee, 
            shipperEarnings,
            SHIPPER_EARNING_RATE.multiply(BigDecimal.valueOf(100)));
        
        return shipperEarnings;
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
