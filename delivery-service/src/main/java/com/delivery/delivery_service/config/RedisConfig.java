package com.delivery.delivery_service.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import lombok.extern.slf4j.Slf4j;

/**
 * ✅ Redis configuration cho Delivery Service (optional - graceful degradation)
 * Chỉ tạo các bean này khi Redis được bật (spring.data.redis.enabled=true hoặc mặc định)
 */
@Configuration
@Slf4j
public class RedisConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true", matchIfMissing = true)
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }

    /**
     * ✅ Redis Message Listener Container – chỉ tạo khi Redis được bật
     */
    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true", matchIfMissing = true)
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        try {
            connectionFactory.getConnection().serverCommands().setConfig("notify-keyspace-events", "Ex");
            log.info("✅ Redis keyspace notifications configured (Ex)");
        } catch (Exception e) {
            log.warn("⚠️ Could not set Redis notify-keyspace-events. Ensure Redis is configured with 'notify-keyspace-events Ex'");
        }

        return container;
    }
}
