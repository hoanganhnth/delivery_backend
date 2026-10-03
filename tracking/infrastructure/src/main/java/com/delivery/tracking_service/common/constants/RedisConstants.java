package com.delivery.tracking_service.common.constants;

public class RedisConstants {
    public static final String SHIPPER_LOCATION_KEY_PREFIX = "shipper:location:";
    
    // TTL in seconds
    public static final long SHIPPER_LOCATION_TTL = 300; // 5 minutes
}
