package com.delivery.delivery_service.common.constants;

import java.math.BigDecimal;

/**
 * ✅ Platform Pricing Constants
 * Commission và phí nền tảng
 */
public class PricingConstants {
    
    // ✅ Platform commission (15% - Grab thường lấy 15-20%)
    public static final BigDecimal PLATFORM_COMMISSION_RATE = new BigDecimal("0.15");
    
    // ✅ Restaurant commission (20% - Phí hoa hồng trên giá món ăn)
    public static final BigDecimal RESTAURANT_COMMISSION_RATE = new BigDecimal("0.20");
    
    // ✅ Shipper earnings rate (85% còn lại)
    public static final BigDecimal SHIPPER_EARNINGS_RATE = new BigDecimal("0.85");
    
    // Minimum shipper earnings (đảm bảo shipper luôn có ít nhất số tiền này)
    public static final BigDecimal MIN_SHIPPER_EARNINGS = new BigDecimal("10000");
    
    private PricingConstants() {
        // Utility class
    }
    
    /**
     * Calculate shipper earnings từ shipping fee
     * Shipper nhận 85% của shipping fee
     */
    public static BigDecimal calculateShipperEarnings(BigDecimal shippingFee) {
        if (shippingFee == null || shippingFee.compareTo(BigDecimal.ZERO) <= 0) {
            return MIN_SHIPPER_EARNINGS;
        }
        
        BigDecimal earnings = shippingFee.multiply(SHIPPER_EARNINGS_RATE);
        
        // Đảm bảo không dưới minimum
        return earnings.compareTo(MIN_SHIPPER_EARNINGS) < 0 ? MIN_SHIPPER_EARNINGS : earnings;
    }
    
    /**
     * Calculate platform commission từ shipping fee
     * Platform lấy 15% của shipping fee
     */
    public static BigDecimal calculatePlatformCommission(BigDecimal shippingFee) {
        if (shippingFee == null || shippingFee.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        return shippingFee.multiply(PLATFORM_COMMISSION_RATE);
    }
}
