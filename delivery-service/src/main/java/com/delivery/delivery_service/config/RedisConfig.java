package com.delivery.delivery_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * ✅ Redis configuration cho Delivery Service
 * Lưu trạng thái "chờ shipper nhận đơn" với TTL + Redis Keyspace Notifications
 */
@Configuration
public class RedisConfig {
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Sử dụng String serializer cho keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Sử dụng Jackson serializer cho values để support LocalDateTime và complex objects
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        
        template.afterPropertiesSet();
        return template;
    }
    
    /**
     * ✅ Redis Message Listener Container for Keyspace Notifications
     * Required for DeliveryKeyExpiredListener to work
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        
        // ✅ Enable Keyspace Notifications for expired events
        // This is equivalent to: CONFIG SET notify-keyspace-events Ex
        // E = Keyevent events, x = Expired events
        try {
            connectionFactory.getConnection().serverCommands().setConfig("notify-keyspace-events", "Ex");
        } catch (Exception e) {
            // Log warning but don't fail - Redis might already have notifications enabled
            System.out.println("⚠️ Warning: Could not set Redis notify-keyspace-events config. " +
                             "Please ensure Redis is configured with 'notify-keyspace-events Ex'");
        }
        
        return container;
    }
}
