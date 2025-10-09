package com.delivery.delivery_service.listener;

import com.delivery.delivery_service.service.DeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * ✅ Redis Keyspace Notifications Listener for automatic shipper retry on TTL expiration
 * Listen for expired keys with pattern "delivery:waiting:{deliveryId}"
 */
@Component
@Slf4j
public class DeliveryKeyExpiredListener extends KeyExpirationEventMessageListener {
    
    private static final String WAITING_KEY_PREFIX = "delivery:waiting:";
    
    private final DeliveryService deliveryService;
    
    public DeliveryKeyExpiredListener(RedisMessageListenerContainer listenerContainer,
                                      DeliveryService deliveryService) {
        super(listenerContainer);
        this.deliveryService = deliveryService;
    }
    
    /**
     * ✅ Handle Redis key expiration event
     * This method is automatically called when a Redis key expires
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String expiredKey = message.toString();
            
            // ✅ Only process delivery waiting keys
            if (expiredKey.startsWith(WAITING_KEY_PREFIX)) {
                String deliveryIdStr = expiredKey.substring(WAITING_KEY_PREFIX.length());
                Long deliveryId = Long.parseLong(deliveryIdStr);
                
                log.warn("⏰ Redis Key Expired - Shipper acceptance timeout for delivery: {} - Auto-retrying shipper search", 
                        deliveryId);
                
                // ✅ Trigger automatic retry via delivery service
                handleShipperAcceptanceTimeout(deliveryId);
            }
            
        } catch (NumberFormatException e) {
            log.error("💥 Invalid delivery ID in expired key: {}", message.toString(), e);
        } catch (Exception e) {
            log.error("💥 Error handling key expiration event: {}", message.toString(), e);
        }
    }
    
    /**
     * ✅ Handle shipper acceptance timeout and trigger retry
     */
    private void handleShipperAcceptanceTimeout(Long deliveryId) {
        try {
            // ✅ Get delivery information for retry
            var delivery = deliveryService.getDeliveryForRetry(deliveryId);
            
            if (delivery != null) {
                log.info("🔄 Triggering automatic shipper retry for delivery: {} after acceptance timeout", deliveryId);
                
                // ✅ This will publish FindShipperEvent again with retry flag
                deliveryService.retryFindShipper(deliveryId);
                
            } else {
                log.warn("⚠️ Delivery not found for retry: {} - may have been completed or cancelled", deliveryId);
            }
            
        } catch (Exception e) {
            log.error("💥 Error triggering shipper retry for delivery: {}: {}", deliveryId, e.getMessage(), e);
        }
    }
}
